##
## R function to call and score "somatic mutations", "RNA editing", or similar events.
## Function takes data in a format output by Bamformatics variantdetails.
## Function scores events based on coverage counts in pairs of samples (e.g.
## coverage in a "normal" sample and a "tumor" sample)
##
## Author: Tomasz Konopka
##



##' Find change events within a pair of samples using a variants details table
##' as output from Bamformatics variantdetails. 
##'
##' @param tab data frame with variant details 
##' @param sA  character string with sample name. tab should contain columns called sA.qual etc.
##'            Sample A is interpreted as the reference sample (e.g. normal tissue)
##' @param sB  character string with sample name. tab should contain columns called sB.qual etc.
##'            Sample B is interpreted as the probant sample (e.g. tumor tissue)
##' @param okfilters character vector. Function gives candidates that have one of these
##'            filter codes. (If variant set has multiple filters that are "ok", e.g.
##'            PASS, exons, exons;cds, all these filter codes and combinations must be specified
##' @param mindepthA numeric, minimum depth in sampleA to call an event
##' @param mindepthB numeric, minimum depth in sampleB to call an event. 
##' @param maxAPA numeric, maximum allelic proportion in sample A 
##' @param minAPB numeric, maximum allelic proportion in sample B 
##' @param minscoreAB numeric, minimum joint score, calculated from data on both samples A and B
##' @param checklow logical. Determines if same criteria must apply for "low" quality counts
##'                   (recommended value is TRUE for SNVs and FALSE for indels)
##'
getVarChangeEvents = function(tab, sA, sB,
  okfilters="PASS",
  mindepthA=4, maxAPA=0.05,
  mindepthB=4, minAPB=0.05, minaltB=3,
  minscoreAB=20, checklow=FALSE) {
  

  ## Helper-function - a wrapper for paste
  ## It concatenates some string without putting in additional spaces
  ## Name of function stands for Pasta Sep Zero
  PSZ = function(...) {
    return(paste(...,sep=""))
  }
  
  ## Helper function - computes allelic proportion and uncertainty
  ## refcount - number of reads with reference base
  ## altcount - number of reads with alternate base
  ## darkcount - used for
  ##
  ## returns - a matrix with three columns.
  ## First column is the allelic proportion (AP), aka the BAF
  ## Second column is an estimate for the uncertainty on the AP based on
  ## a normal approximateion to a poisson model
  ## Third column is a "score", which is the 10* AP / error
  allelicproportion = function(refcount, altcount, darkcount=1) {    
    AP = altcount/(refcount+altcount)
    APnumerator = sqrt((refcount+darkcount)*(altcount+darkcount))
    APdenominator = ((refcount+altcount+(2*darkcount))^(3/2))
    APerr = APnumerator / APdenominator
    return (cbind(AP, APerr, score=10*AP/APerr))  
  }
  
  
  ## take the input table and get rid of the unnecessary columns
  ABcols = c(".qual",".cov",".cov.low",".ref",".ref.low",".alt",".alt.low",".filter")
  basecols = c("chr","position","dbSNP","refBase","altBase")
  tab = tab[,c(basecols, PSZ(sA,ABcols), PSZ(sB,ABcols))]  
  
  ## make sure .ref and .alt rows are all zero or positive
  for (xx in c(".ref",".ref.low",".alt",".alt.low")) {
    tab[tab[,PSZ(sA,xx)]<0, PSZ(sA,xx)] = 0
    tab[tab[,PSZ(sB,xx)]<0, PSZ(sB,xx)] = 0
  }

  ## impose thresholds on sample A
  tab = tab[tab[,PSZ(sA,".cov")]>=mindepthA, ,drop=FALSE] 
  tab = tab[tab[,PSZ(sA,".alt")]/tab[,PSZ(sA,".cov")] < maxAPA, ,drop=FALSE]
  temp.alt = tab[,PSZ(sA, ".alt")]+tab[,PSZ(sA, ".alt.low")]
  temp.cov = tab[,PSZ(sA, ".cov")]+tab[,PSZ(sA, ".cov.low")]
  tab = tab[(temp.alt/temp.cov) < maxAPA, ,drop=FALSE]
  rm(temp.alt, temp.cov)
  
  ## impose threshold on sample B
  tab = tab[tab[,PSZ(sB,".cov")]>=mindepthB, ,drop=FALSE]
  tab = tab[tab[,PSZ(sB,".alt")]/tab[,PSZ(sB,".cov")] > minAPB, ,drop=FALSE]
  temp.alt = tab[,PSZ(sB, ".alt")]+tab[,PSZ(sB, ".alt.low")]
  temp.cov = tab[,PSZ(sB, ".cov")]+tab[,PSZ(sB, ".cov.low")]
  tab = tab[(temp.alt/temp.cov)>minAPB, ,drop=FALSE]
  rm(temp.alt, temp.cov)
  tab = tab[tab[,PSZ(sB,".alt")]>=minaltB, ,drop=FALSE]
  tab = tab[tab[,PSZ(sB,".filter")] %in% okfilters, ,drop=FALSE]
   
  ## calculate the event score
  ## impose threshold on joint AB
  apA = allelicproportion(tab[,PSZ(sA,".ref")], tab[,PSZ(sA,".alt")])
  apB = allelicproportion(tab[,PSZ(sB,".ref")], tab[,PSZ(sB,".alt")])
  apA[!is.finite(apA[,1]),1] = 0
  apB[!is.finite(apB[,1]),1] = 0
  apAB = apB[,1]-apA[,1]
  apUnc = sqrt((apB[,2])^2 + (apA[,2])^2)
  tab = cbind(tab, event.score=10*apAB/apUnc)
  tab = tab[,c(basecols, "event.score", PSZ(sA, ABcols), PSZ(sB, ABcols))]

  ## get only hits with a minimal score
  tab = tab[tab[,"event.score"]>= minscoreAB, ,drop=FALSE]
  
  ## repeat some of the simple threshold on the low quality items
  if (checklow) {
    temp = tab[,PSZ(sA,".alt.low")]/tab[,PSZ(sA,".cov.low")] < maxAPA
    temp[!is.finite(temp)] = FALSE
    tab = tab[temp, ,drop=FALSE]
  }

  ## finally, add columns for the AP in sample A and B
  apA = tab[,PSZ(sA,".alt")]/tab[,PSZ(sA,".cov")]
  apB = tab[,PSZ(sB,".alt")]/tab[,PSZ(sB,".cov")]

  tab = cbind(tab[,c(basecols,"event.score")], apA=apA, apB=apB)
  colnames(tab) = c(basecols,"event.score", PSZ(sA,".BAF"), PSZ(sB,".BAF"))
  
  return(tab)
}

