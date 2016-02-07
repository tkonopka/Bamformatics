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
package bamfo.gui.configurations;

import bamfo.utils.BamfoSettings;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import jsequtils.file.BufferedReaderMaker;
import jsequtils.file.OutputStreamMaker;

/**
 *
 * @author tomasz
 */
public class CallConfiguration extends Configuration {

    // The class inherits name and suffix from the "Configuration" class
    //    
    // the settings will hold choices from minfrostart, mindepth, etc.
    private BamfoSettings settings;
    // the annotation path is set to empty, i.e. no annotations will take place
    // I don't use null in order to avoid having problems with parsing/setting
    private String annotationfilepath = "";
    private boolean replaceQual = false;

    /**
     * Constructor that creates an empty configuration without any filters.
     *
     * @param name
     */
    public CallConfiguration(String name) {
        setName(name);
        setSuffix("." + name + ".vcf.gz");
        settings = new BamfoSettings();
        annotationfilepath = "";
        replaceQual = false;
    }

    /**
     * Loads a configuration saved in a text file.
     *
     * @param f
     * @throws IOException
     */
    public CallConfiguration(File f) throws IOException {
        BufferedReader br = BufferedReaderMaker.makeBufferedReader(f);
        String s = br.readLine();
        setName(s);
        s = br.readLine();
        setSuffix(s);

        // for the other settings, read them in option/value pairs
        settings = new BamfoSettings();
        while ((s = br.readLine()) != null) {

            String[] temp = s.split("\t");
            if (temp[0].equals("minbasequal")) {
                settings.setMinbasequal((byte) temp[1].charAt(0));
            } else if (temp[0].equals("minmapqual")) {
                settings.setMinmapqual(Integer.parseInt(temp[1]));
            } else if (temp[0].equals("genome")) {
                settings.setGenome(temp[1]);
            } else if (temp[0].equals("minallelic")) {
                settings.setMinallelic(Double.parseDouble(temp[1]));
            } else if (temp[0].equals("mindepth")) {
                settings.setMindepth(Integer.parseInt(temp[1]));
            } else if (temp[0].equals("minfromend")) {
                settings.setMinfromend(Integer.parseInt(temp[1]));
            } else if (temp[0].equals("minfromstart")) {
                settings.setMinfromstart(Integer.parseInt(temp[1]));
            } else if (temp[0].equals("minscore")) {
                settings.setMinscore(Double.parseDouble(temp[1]));
            } else if (temp[0].equals("strandbias")) {
                settings.setStrandbias(Double.parseDouble(temp[1]));
            } else if (temp[0].equals("trimQB")) {
                settings.setTrimBtail((int) Integer.parseInt(temp[1]) == 1);
            } else if (temp[0].equals("trim")) {
                settings.setTrimpolyedge((int) Integer.parseInt(temp[1]) == 1);
            } else if (temp[0].equals("NRef")) {
                settings.setNRef((int) Integer.parseInt(temp[1]) == 1);
            } else if (temp[0].equals("annotationdb")) {
                if (temp.length > 1) {
                    this.annotationfilepath = temp[1];
                }
            } else if (temp[0].equals("replacequal")) {
                if (temp[1].equals("true")) {
                    replaceQual = true;
                } else {
                    replaceQual = false;
                }
            }
        }

        br.close();

    }

    /**
     * Writes a filter configuration into a file
     *
     * @param f
     */
    public void saveConfiguration(File f) {

        try {
            OutputStream os = OutputStreamMaker.makeOutputStream(f);
            StringBuilder sb = new StringBuilder();
            sb.append(getName()).append("\n");
            sb.append(getSuffix()).append("\n");
            sb.append("genome\t").append(settings.getGenome()).append("\n");
            sb.append("minallelic\t").append(settings.getMinallelic()).append("\n");
            sb.append("minbasequal\t").append((char) settings.getMinbasequal()).append("\n");
            sb.append("mindepth\t").append(settings.getMindepth()).append("\n");
            sb.append("minfromend\t").append(settings.getMinfromend()).append("\n");
            sb.append("minfromstart\t").append(settings.getMinfromstart()).append("\n");
            sb.append("minmapqual\t").append(settings.getMinmapqual()).append("\n");
            sb.append("minscore\t").append(settings.getMinscore()).append("\n");
            sb.append("strandbias\t").append(settings.getStrandbias()).append("\n");
            if (settings.isTrimBtail()) {
                sb.append("trimBtail\t1\n");
            } else {
                sb.append("trimBtail\t0\n");
            }
            if (settings.isTrimpolyedge()) {
                sb.append("trim\t1\n");
            } else {
                sb.append("trim\t0\n");
            }
            if (settings.isNRef()) {
                sb.append("NRef\t1\n");
            } else {
                sb.append("NRef\t0\n");
            }

            sb.append("annotationdb\t");
            if (annotationfilepath == null) {
                sb.append("\t\n");
            } else {
                sb.append(this.annotationfilepath).append("\n");
            }
            sb.append("replacequal\t").append(this.replaceQual).append("\n");
            os.write(sb.toString().getBytes());

            os.close();
        } catch (Exception ex) {
            System.out.println("Error saving configuration");
        }

    }

    public BamfoSettings getSettings() {
        return settings;
    }

    public String getAnnotationfilepath() {
        return annotationfilepath;
    }

    public void setAnnotationfilepath(String annotationfilepath) {
        this.annotationfilepath = annotationfilepath;
    }

    public boolean isReplaceQual() {
        return replaceQual;
    }

    public void setReplaceQual(boolean replaceQual) {
        this.replaceQual = replaceQual;
    }
}
