/*
 * Copyright 2013 Tomasz Konopka.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package bamfo.gui.viewers;

import bamfo.gui.BamfoGUI;
import bamfo.gui.utils.BamfoRunnableTask;
import bamfo.utils.DateInterval;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import javax.swing.ImageIcon;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.Timer;
import jsequtils.file.OutputStreamMaker;

/**
 * A panel that provides a way to display and control a task. The viewer accepts
 * one runnable task as the main workload. It can also accept a second runnable
 * task for post-work.
 *
 * The first runnable task is submitted to an executor. The finishing up
 * runnable is executed as is, so it should not be too complicated.
 *
 * The viewer consists of a toolbar and some panels in card layout. One card
 * shows information about the submitted task. Another card shows log messages.
 *
 * The toolbar provides basic controls to run/stop the tasks.
 *
 *
 * @author tomasz
 */
public class BamfoTaskViewer extends BamfoViewer {

    // the task has to be set post initialization    
    private BamfoRunnableTask task = null;
    // taskfuture will be used to get a handle on the task
    private Future taskfuture = null;
    private Runnable finishtask = null;
    private boolean executed = false;
    private final ExecutorService service;
    private final JTabbedPane pane;
    private String descriptor = "task";
    private String prefix = "";
    private final static ImageIcon successIcon = new ImageIcon(BamfoGUI.class.getResource("icons/tick.png"));
    private final static ImageIcon failIcon = new ImageIcon(BamfoGUI.class.getResource("icons/cross.png"));
    // by default the, status updates will be done every:
    private final static int WAIT_TIMEOUT = 1000;
    private final Timer statusTimer;
    private final static SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
    private Date startDate, endDate;

    /**
     * Creates new panel with a TaskViewer. This requires to know what executor
     * service will be used.
     *
     */
    public BamfoTaskViewer(final ExecutorService service, final JTabbedPane pane, String prefix) {
        initComponents();
        this.service = service;
        this.pane = pane;
        this.prefix=prefix;

        // show the settings panel
        viewDetailsButton.setSelected(true);
        ((CardLayout) taskMainPanel.getLayout()).show(taskMainPanel, "settingsCard");

        // by default the execute and stop buttons will be inactive
        executeButton.setEnabled(false);
        stopButton.setEnabled(false);

        // hide the result label
        resultLabel.setText("Unitialized");

        // create a timer that will check the status of the runnable
        // and update some icons/labels when it is finished.
        statusTimer = new Timer(WAIT_TIMEOUT, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int taskvalue = task.getExitval();
                if (taskvalue >= 0) {
                    statusTimer.stop();

                    // perform the finishing up task if there is one
                    if (finishtask != null) {
                        finishtask.run();
                    }

                    endDate = new Date();
                    endTimeLabel.setText(sdf.format(endDate));
                    stopButton.setEnabled(false);
                    runningTimeLabel.setText(DateInterval.timeInterval(startDate, endDate));
                }
                if (taskvalue == 0) {
                    resultLabel.setIcon(successIcon);
                    //resultLabel.setText("Completed");
                    setTaskStatus("Done");
                } else if (taskvalue > 0) {
                    resultLabel.setIcon(failIcon);
                    //resultLabel.setText("Failed");
                    setTaskStatus("Fail");
                }
            }
        });

        startTimeLabel.setText("");
        endTimeLabel.setText("");
        runningTimeLabel.setText("");
        setTaskStatus("Running");
    }

    /**
     * This defined the main task to be performed.
     *
     * @param task
     */
    public synchronized void setTask(BamfoRunnableTask task) {
        this.task = task;
        executeButton.setEnabled(true);
        stopButton.setEnabled(false);
        resultLabel.setText("Ready");
        argumentsTextArea.setText(task.getArguments());
        commandTextArea.setText(task.getExecutable());
        dirTextArea.setText(task.getDirectory());
    }

    /**
     * Set a task to be performed after the main task, i.e. to finish up or
     * clean up.
     *
     * @param task
     */
    public synchronized void setFinishTask(Runnable task) {
        this.finishtask = task;
    }

    /**
     * Update the status of the script that appears next to the play/stop
     * buttons and in the tab title in the GUI
     *
     * @param status
     */
    public final void setTaskStatus(String status) {
        synchronized (pane) {            
            resultLabel.setText(status);            
            Component[] comparray = pane.getComponents();                  
            int numcomps = pane.getComponentCount();            
            int thisindex = -1;            
            for (int i = 0; i < numcomps; i++) {
                comparray[i].getTreeLock();                
                if (comparray[i] == this) {
                    thisindex = i;                    
                    i = comparray.length+1;                    
                }                
            }            
            if (thisindex >= 0) {
                // this adjustment seems to be necessary, but why?
                if (thisindex>0) {
                    thisindex--;
                }                
                File f = new File(pane.getToolTipTextAt(thisindex));                
                pane.setTitleAt(thisindex, prefix+f.getName() + " [" + status + "]");                
            }
        }
    }

    /**
     * actually passes the task to the executor.
     *
     */
    public void executeTask() {
        if (task != null && !executed) {
            executeButton.setEnabled(false);
            stopButton.setEnabled(true);
            resultLabel.setText("Running...");
            startDate = new Date();
            //service.execute(task);
            taskfuture = service.submit(task);
            statusTimer.start();
            startTimeLabel.setText(sdf.format(startDate));

            // also change the text in the GUI tabbed pane
            setTaskStatus("Running");
        }
    }

    public JTextArea getOutputArea() {
        return this.outputTextArea;
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        groupSettingsOutput = new javax.swing.ButtonGroup();
        jToolBar1 = new javax.swing.JToolBar();
        viewDetailsButton = new javax.swing.JToggleButton();
        logOutputButton = new javax.swing.JToggleButton();
        jSeparator2 = new javax.swing.JToolBar.Separator();
        executeButton = new javax.swing.JButton();
        stopButton = new javax.swing.JButton();
        jSeparator3 = new javax.swing.JToolBar.Separator();
        resultLabel = new javax.swing.JLabel();
        taskMainPanel = new javax.swing.JPanel();
        outputScrollPane = new javax.swing.JScrollPane();
        outputTextArea = new javax.swing.JTextArea();
        settingsPanel = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        jScrollPane1 = new javax.swing.JScrollPane();
        argumentsTextArea = new javax.swing.JTextArea();
        jLabel3 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        jScrollPane2 = new javax.swing.JScrollPane();
        commandTextArea = new javax.swing.JTextArea();
        startTimeLabel = new javax.swing.JLabel();
        endTimeLabel = new javax.swing.JLabel();
        jLabel5 = new javax.swing.JLabel();
        runningTimeLabel = new javax.swing.JLabel();
        jLabel6 = new javax.swing.JLabel();
        jScrollPane3 = new javax.swing.JScrollPane();
        dirTextArea = new javax.swing.JTextArea();

        setLayout(new javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS));

        jToolBar1.setFloatable(false);
        jToolBar1.setRollover(true);
        jToolBar1.setMaximumSize(new java.awt.Dimension(32767, 30));
        jToolBar1.setMinimumSize(new java.awt.Dimension(300, 30));
        jToolBar1.setPreferredSize(new java.awt.Dimension(400, 30));

        groupSettingsOutput.add(viewDetailsButton);
        viewDetailsButton.setText("Details");
        viewDetailsButton.setFocusable(false);
        viewDetailsButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        viewDetailsButton.setMaximumSize(new java.awt.Dimension(80, 25));
        viewDetailsButton.setMinimumSize(new java.awt.Dimension(80, 25));
        viewDetailsButton.setPreferredSize(new java.awt.Dimension(80, 25));
        viewDetailsButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        viewDetailsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                viewDetailsButtonActionPerformed(evt);
            }
        });
        jToolBar1.add(viewDetailsButton);

        groupSettingsOutput.add(logOutputButton);
        logOutputButton.setText("Log");
        logOutputButton.setBorder(javax.swing.BorderFactory.createEmptyBorder(6, 6, 6, 6));
        logOutputButton.setFocusable(false);
        logOutputButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        logOutputButton.setMaximumSize(new java.awt.Dimension(80, 25));
        logOutputButton.setMinimumSize(new java.awt.Dimension(80, 25));
        logOutputButton.setPreferredSize(new java.awt.Dimension(80, 25));
        logOutputButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        logOutputButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                logOutputButtonActionPerformed(evt);
            }
        });
        jToolBar1.add(logOutputButton);
        jToolBar1.add(jSeparator2);

        executeButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/bamfo/gui/icons/control.png"))); // NOI18N
        executeButton.setEnabled(false);
        executeButton.setFocusable(false);
        executeButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        executeButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        executeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                executeButtonActionPerformed(evt);
            }
        });
        jToolBar1.add(executeButton);

        stopButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/bamfo/gui/icons/control-stop-square.png"))); // NOI18N
        stopButton.setEnabled(false);
        stopButton.setFocusable(false);
        stopButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        stopButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        stopButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                stopButtonActionPerformed(evt);
            }
        });
        jToolBar1.add(stopButton);
        jToolBar1.add(jSeparator3);

        resultLabel.setText("result");
        jToolBar1.add(resultLabel);

        add(jToolBar1);

        taskMainPanel.setLayout(new java.awt.CardLayout());

        outputScrollPane.setName(""); // NOI18N

        outputTextArea.setEditable(false);
        outputTextArea.setColumns(20);
        outputTextArea.setRows(5);
        outputTextArea.setMargin(new java.awt.Insets(2, 2, 2, 2));
        outputScrollPane.setViewportView(outputTextArea);

        taskMainPanel.add(outputScrollPane, "outputCard");

        jLabel1.setText("Script:");
        jLabel1.setMinimumSize(new java.awt.Dimension(90, 17));
        jLabel1.setPreferredSize(new java.awt.Dimension(100, 17));

        jLabel2.setText("Arguments:");
        jLabel2.setMinimumSize(new java.awt.Dimension(100, 17));
        jLabel2.setPreferredSize(new java.awt.Dimension(90, 17));

        argumentsTextArea.setEditable(false);
        argumentsTextArea.setColumns(20);
        argumentsTextArea.setRows(5);
        jScrollPane1.setViewportView(argumentsTextArea);

        jLabel3.setText("Start time:");
        jLabel3.setPreferredSize(new java.awt.Dimension(100, 17));

        jLabel4.setText("End time:");
        jLabel4.setPreferredSize(new java.awt.Dimension(100, 17));

        jScrollPane2.setMinimumSize(new java.awt.Dimension(23, 18));
        jScrollPane2.setPreferredSize(new java.awt.Dimension(279, 18));

        commandTextArea.setEditable(false);
        commandTextArea.setColumns(20);
        commandTextArea.setRows(1);
        commandTextArea.setMinimumSize(new java.awt.Dimension(0, 18));
        commandTextArea.setPreferredSize(new java.awt.Dimension(260, 18));
        jScrollPane2.setViewportView(commandTextArea);

        startTimeLabel.setText("jLabel5");

        endTimeLabel.setText("jLabel6");

        jLabel5.setText("Running time:");
        jLabel5.setPreferredSize(new java.awt.Dimension(100, 17));

        runningTimeLabel.setText("jLabel6");

        jLabel6.setText("Directory:");

        dirTextArea.setEditable(false);
        dirTextArea.setColumns(20);
        dirTextArea.setRows(1);
        dirTextArea.setMinimumSize(new java.awt.Dimension(0, 18));
        dirTextArea.setPreferredSize(new java.awt.Dimension(260, 18));
        jScrollPane3.setViewportView(dirTextArea);

        javax.swing.GroupLayout settingsPanelLayout = new javax.swing.GroupLayout(settingsPanel);
        settingsPanel.setLayout(settingsPanelLayout);
        settingsPanelLayout.setHorizontalGroup(
            settingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(settingsPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(settingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(settingsPanelLayout.createSequentialGroup()
                        .addGroup(settingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jLabel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jLabel6))
                        .addGap(18, 18, 18)
                        .addGroup(settingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jScrollPane1)
                            .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 323, Short.MAX_VALUE)
                            .addComponent(jScrollPane3, javax.swing.GroupLayout.DEFAULT_SIZE, 323, Short.MAX_VALUE)))
                    .addGroup(settingsPanelLayout.createSequentialGroup()
                        .addGroup(settingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(settingsPanelLayout.createSequentialGroup()
                                .addComponent(jLabel5, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(runningTimeLabel))
                            .addGroup(settingsPanelLayout.createSequentialGroup()
                                .addComponent(jLabel3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(startTimeLabel))
                            .addGroup(settingsPanelLayout.createSequentialGroup()
                                .addComponent(jLabel4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(endTimeLabel)))
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        settingsPanelLayout.setVerticalGroup(
            settingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(settingsPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(settingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane3, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel6))
                .addGap(12, 12, 12)
                .addGroup(settingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(12, 12, 12)
                .addGroup(settingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 149, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(settingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(startTimeLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(settingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(endTimeLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(settingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel5, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(runningTimeLabel))
                .addContainerGap(43, Short.MAX_VALUE))
        );

        taskMainPanel.add(settingsPanel, "settingsCard");

        add(taskMainPanel);
    }// </editor-fold>//GEN-END:initComponents

    private void viewDetailsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_viewDetailsButtonActionPerformed
        CardLayout cl = (CardLayout) taskMainPanel.getLayout();
        cl.show(taskMainPanel, "settingsCard");
    }//GEN-LAST:event_viewDetailsButtonActionPerformed

    private void logOutputButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_logOutputButtonActionPerformed
        CardLayout cl = (CardLayout) taskMainPanel.getLayout();
        cl.show(taskMainPanel, "outputCard");
    }//GEN-LAST:event_logOutputButtonActionPerformed

    private void stopButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_stopButtonActionPerformed
        // this will try to stop the task
        task.stopTask();
        taskfuture.cancel(true);
        setTaskStatus("Stopping");
        stopButton.setEnabled(false);
    }//GEN-LAST:event_stopButtonActionPerformed

    private void executeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_executeButtonActionPerformed
        if (this.task != null) {
            this.executeTask();
        }
    }//GEN-LAST:event_executeButtonActionPerformed
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JTextArea argumentsTextArea;
    private javax.swing.JTextArea commandTextArea;
    private javax.swing.JTextArea dirTextArea;
    private javax.swing.JLabel endTimeLabel;
    private javax.swing.JButton executeButton;
    private javax.swing.ButtonGroup groupSettingsOutput;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JToolBar.Separator jSeparator2;
    private javax.swing.JToolBar.Separator jSeparator3;
    private javax.swing.JToolBar jToolBar1;
    private javax.swing.JToggleButton logOutputButton;
    private javax.swing.JScrollPane outputScrollPane;
    private javax.swing.JTextArea outputTextArea;
    private javax.swing.JLabel resultLabel;
    private javax.swing.JLabel runningTimeLabel;
    private javax.swing.JPanel settingsPanel;
    private javax.swing.JLabel startTimeLabel;
    private javax.swing.JButton stopButton;
    private javax.swing.JPanel taskMainPanel;
    private javax.swing.JToggleButton viewDetailsButton;
    // End of variables declaration//GEN-END:variables

    @Override
    public String getViewerDescriptor() {
        return descriptor;
    }
    
    @Override
    public void saveAs(File savefile) {
        if (!overwrite(savefile)) {
            return;
        }
        this.savefile = savefile;
        try {
            OutputStream outstream = OutputStreamMaker.makeOutputStream(savefile);
            outstream.write(outputTextArea.getText().getBytes());
            outstream.close();
        } catch (Exception ex) {            
            System.out.println("Error writing: "+ex.getMessage());
        }    
        
    }
        
}
