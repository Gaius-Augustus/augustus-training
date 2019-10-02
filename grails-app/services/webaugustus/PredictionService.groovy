package webaugustus

import grails.gorm.transactions.Transactional
import javax.annotation.PostConstruct
import webaugustus.AbstractWebAugustusDomainClass

/**
 * The class PredictionService controls everything that is related to a job for predicting genes with pre-trained parameters on a novel genome
 *    - it handles the file upload by wget
 *    - format check
 *    - job submission and status checks
 *    - rendering of results/job status page
 *    - sending E-Mails concerning the job status (downloaded files, errors, finished)
 */
@Transactional
class PredictionService extends AbstractWebaugustusService {

    // need to adjust the output dir to whatever working dir! This is where uploaded files and results will be saved.
    private static final String output_dir =     "/data/www/webaugustus/webdata/augpred"    // adapt to the actual situation // should be something in home of webserver user and augustus frontend user
    private static final String web_output_dir = "/data/www/webaugustus/prediction-results" // adapt to the actual situation // must be writable to webserver application
    // web-output - directory to the results that are downloadable by end users
    private static final String web_output_url = "${web_output_base_url}prediction-results/" // "http://bioinf.uni-greifswald.de/trainaugustus/prediction-results/" // adapt to the actual situation
    private static final String prediction_url_part = "prediction/"
    
    // this log File contains the "process log", what was happening with which job when.
    private static final File logFile = new File("${output_dir}/pred.log")
    private static final int verb = 3 // 1 only basic log messages, 2 all issued commands, 3 also script content
    
    public void sendMailToUser(Prediction predictionInstance, String subjectString, String message) {
        sendMailToUser(predictionInstance.email_adress, subjectString, message)
    }
    
    @PostConstruct
    def init() {
        Utilities.log(logFile, 1, verb, "startup     ", "PredictionService")
        startWorkerThread()
    }
    
    public String getOutputDir() {
        return PredictionService.output_dir
    }
    
    public String getWebOutputDir() {
        return PredictionService.web_output_dir
    }
    
    public String getWebOutputURL() {
        return PredictionService.web_output_url
    }
    
    public String getHttpBaseURL() {
        return AbstractWebaugustusService.http_base_url + prediction_url_part
    }
    
    public File getLogFile() {
        return PredictionService.logFile
    }
    
    public int getVerboseLevel() {
        return PredictionService.verb
    }
    
    public String getServiceName() {
        return "PredictionService"
    }
    
    /**
     * Returns all prediction instances where the user has committed a job, but this job has not yet startet
     */
    protected List<Prediction> findCommittedJobs() {
        return Prediction.withTransaction { Prediction.findAll(sort:"dateCreated", order: "asc"){ // query returns all committed jobs
            job_status == '0'
        } }
    }

    /**
     * Returns all prediction instances where the job is started
     */
    protected List<Prediction> findSubmittedJobs() {
        return Prediction.withTransaction { Prediction.findAll(sort:"dateCreated", order: "asc"){ // query returns all submitted jobs
            job_status == '1' || 
            job_status == '2' || 
            job_status == '3'
        } }
    }
    
    private void deleteDir(Prediction predictionInstance) {
        String dirName = "${output_dir}/${predictionInstance.accession_id}"
        Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Project directory ${dirName} is deleted")
        def cmd = ["rm -r ${dirName} &> /dev/null"]
        Utilities.execute(logFile, 2, predictionInstance.accession_id, "removeProjectDir", cmd)
    }
    
    private void abortJob(Prediction predictionInstance) {
        abortJob(predictionInstance, null)
    }
    
    private void abortJob(Prediction predictionInstance, String message) {
        abortJob(predictionInstance, message, "5")
    }
    
    private void abortJob(Prediction predictionInstance, String message, String jobStatus) {
        deleteDir(predictionInstance)
        
        if(predictionInstance.email_adress == null) {
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Job ${predictionInstance.accession_id} by anonymous user is aborted!")
        }
        else {
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Job ${predictionInstance.accession_id} is aborted!")
        }
        
        if (message != null) {
            predictionInstance.message = "${predictionInstance.message}-----------------------------"
            predictionInstance.message = "${predictionInstance.message}-----------------\n${new Date()}"
            predictionInstance.message = "${predictionInstance.message} - Error Message:\n----------"
            predictionInstance.message = "${predictionInstance.message}-----------------------------"
            predictionInstance.message = "${predictionInstance.message}------\n\n${message}"

            sendMailToUser(predictionInstance, "Your AUGUSTUS prediction job ${predictionInstance.accession_id} was aborted", message)
        }
        
        predictionInstance.results_urls = null
        predictionInstance.job_status = jobStatus
        predictionInstance.save(flush: true)
    }
    
    /**
     * Download for the given prediction instance all data provided by urls, check the data and start the augustus job
     */
    @Transactional
    protected void loadDataAndStartJob(AbstractWebAugustusDomainClass instance) {
        Prediction predictionInstance = (Prediction) instance
        Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "load and start job ")
        
        String dirName = "${output_dir}/${predictionInstance.accession_id}"
        File projectDir = new File(dirName)
            
        // retrieve genome file
        if (predictionInstance.genome_ftp_link != null) {
            projectDir.mkdirs()

            def cmd = ["wget -O ${dirName}/genome.fa ${predictionInstance.genome_ftp_link}  &> /dev/null"]
            Utilities.execute(logFile, verb, predictionInstance.accession_id, "getGenomeScript", cmd)

            if("${predictionInstance.genome_ftp_link}" =~ /\.gz/){
                cmd = ["mv ${dirName}/genome.fa ${dirName}/genome.fa.gz; gunzip ${dirName}/genome.fa.gz"]
                Utilities.execute(logFile, verb, predictionInstance.accession_id, "gunzipGenomeScript", cmd)
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Unpacked genome file.")
            }
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "genome file upload finished, file stored as genome.fa at ${dirName}")
            // check number of scaffolds (to avoid Java heapspace error in the next step)
            cmd = ["grep -c '>' ${dirName}/genome.fa"]
            Long nSeqNumber = Utilities.executeForLong(logFile, verb, predictionInstance.accession_id, "nSeqFile", cmd)
            int maxNSeqs = getMaxNSeqs()
            if(nSeqNumber == null || nSeqNumber > maxNSeqs){
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "The genome file contains more than ${maxNSeqs} scaffolds: ${nSeqNumber}. Aborting job.");
                String mailStr = "Your AUGUSTUS prediction job ${predictionInstance.accession_id} was aborted\nbecause the provided genome file\n${predictionInstance.genome_ftp_link}\ncontains more than ${maxNSeqs} scaffolds (${nSeqNumber} scaffolds). This is not allowed!\n\n"
                abortJob(predictionInstance, mailStr)
                return
            }

            // check for fasta format & get seq names for gff validation:
            def metacharacterFlag = 0
            def seqNames = []
            def genomeFastaFlag = 0      
            new File(projectDir, "genome.fa").eachLine{line ->
                if(line =~ /\*/ || line =~ /\?/){
                    metacharacterFlag = 1
                }else{
                    if(!(line =~ /^[>AaTtGgCcHhXxRrYyWwSsMmKkBbVvDdNn]/) && !(line =~ /^$/)){ genomeFastaFlag = 1 }
                    if(line =~ /^>/){
                        def len = line.length()
                        seqNames << line[1..(len-1)]
                    }
                }
            }
            if(metacharacterFlag == 1){
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "The genome file contains metacharacters (e.g. * or ?).");
                String mailStr = "Your AUGUSTUS prediction job ${predictionInstance.accession_id} was aborted\nbecause the provided genome file\n${predictionInstance.genome_ftp_link}\ncontains metacharacters (e.g. * or ?). This is not allowed.\n\n"
                abortJob(predictionInstance, mailStr)
                return
            }
            if(genomeFastaFlag == 1) {
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "The genome file was not fasta.")
                String mailStr = "Your AUGUSTUS prediction job ${predictionInstance.accession_id} was aborted\nbecause the provided genome file\n${predictionInstance.genome_ftp_link}\nwas not in DNA fasta format.\n\n"
                abortJob(predictionInstance, mailStr)
                return
            }
            
            // check gff format
            def gffColErrorFlag = 0
            def gffNameErrorFlag = 0
            def gffSourceErrorFlag = 0
            File structFile = new File(projectDir, "hints.gff")
            if (structFile.exists() && predictionInstance.genome_ftp_link != null) { // if seqNames already exists
                // gff format validation: number of columns 9, + or - in column 7, column 1 has to be member of seqNames
                Utilities.log(logFile, 2, verb, predictionInstance.accession_id, "Checking hints.gff file format")
                def gffArray
                def isElement
                metacharacterFlag = 0
                structFile.eachLine{line ->
                    if(line =~ /\*/ || line =~ /\?/){
                        metacharacterFlag = 1
                    }else{
                        gffArray = line.split("\t")
                        if(!(gffArray.size() == 9)){
                            gffColErrorFlag = 1
                        }else{
                            isElement = 0
                            seqNames.each{ seq ->
                                if(seq =~ /${gffArray[0]}/){ isElement = 1 }
                                if(isElement == 0){ gffNameErrorFlag = 1 }
                                if(!("${gffArray[8]}" =~ /source=M/)){gffSourceErrorFlag = 1}
                            }
                        }
                    }
                }
                if(metacharacterFlag == 1){
                    Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "The hints file contains metacharacters (e.g. * or ?).");
                    String mailStr = "Your AUGUSTUS prediction job ${predictionInstance.accession_id} was aborted because the provided hints file\ncontains metacharacters (e.g. * or ?). This is not allowed.\n\n"
                    abortJob(predictionInstance, mailStr)
                    return
                }
                if(gffColErrorFlag == 1){
                    Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Hints file does not always contain 9 columns.")
                    String mailStr = "Your AUGUSTUS prediction job ${predictionInstance.accession_id} was aborted because the provided hints file\n${predictionInstance.hint_file}\ndid not contain 9 columns in each line. Please make sure the gff-format complies\nwith the instructions in our 'Help' section before submitting another job!\n\n"
                    abortJob(predictionInstance, mailStr)
                    return
                }
                if(gffNameErrorFlag == 1){
                    Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Hints file contains entries that do not comply with genome sequence names.")
                    String mailStr = "Your AUGUSTUS prediction job ${predictionInstance.accession_id} was aborted because the sequence names in\nthe provided hints file\n${predictionInstance.hint_file}\ndid not comply with the sequence names in the supplied genome file\n${predictionInstance.genome_ftp_link}.\nPlease make sure the gff-format complies with the instructions in our 'Help' section\nbefore submitting another job!\n\n"
                    abortJob(predictionInstance, mailStr)
                    return
                }
                if(gffSourceErrorFlag ==1){
                    Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Hints file contains entries that do not have source=M in the last column.")
                    String mailStr = "Your AUGUSTUS prediction job ${predictionInstance.accession_id} was aborted because the last column of your\nhints file\n${predictionInstance.hint_file}\ndoes not contain the content source=M. Please make sure the gff-format complies with\nthe instructions in our 'Help' section before submitting another job!\n\n"
                    abortJob(predictionInstance, mailStr)
                    return
                }
            }

            cmd = ["cksum ${dirName}/genome.fa"]
            predictionInstance.genome_cksum = Utilities.executeForLong(logFile, verb, predictionInstance.accession_id, "genomeCksumScript", cmd, "(\\d*) \\d* ")
            predictionInstance.genome_size =  Utilities.executeForLong(logFile, verb, predictionInstance.accession_id, "genomeCksumScript", cmd, "\\d* (\\d*) ")
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "genome.fa is ${predictionInstance.genome_size} big and has a cksum of ${predictionInstance.genome_cksum}.")
        } // end of if(!(predictionInstance.genome_ftp_link == null))

        // retrieve EST file
        if(!(predictionInstance.est_ftp_link == null)){

            def cmd = ["wget -O ${dirName}/est.fa ${predictionInstance.est_ftp_link}  &> /dev/null"]
            Utilities.execute(logFile, verb, predictionInstance.accession_id, "getEstScript", cmd)

            if("${predictionInstance.est_ftp_link}" =~ /\.gz/){
                cmd = ["mv ${dirName}/est.fa ${dirName}/est.fa.gz; gunzip ${dirName}/est.fa.gz"]
                Utilities.execute(logFile, verb, predictionInstance.accession_id, "gunzipEstScript", cmd)
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Unpacked EST file.")
            }
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "EST/cDNA file upload finished, file stored as est.fa at ${dirName}")
            // check for fasta format:
            def metacharacterFlag = 0
            def estFastaFlag = 0
            new File(projectDir, "est.fa").eachLine{line ->
                if(line =~ /\*/ || line =~ /\?/){
                    metacharacterFlag = 1
                }else{
                    if(!(line =~ /^[>AaTtGgCcHhXxRrYyWwSsMmKkBbVvDdNn]/) && !(line =~ /^$/)){
                        estFastaFlag = 1
                    }
                }
            }
            if(metacharacterFlag == 1){
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "The cDNA file contains metacharacters (e.g. * or ?).");
                String mailStr = "Your AUGUSTUS prediction job ${predictionInstance.accession_id} was aborted\nbecause the provided cDNA file\n${predictionInstance.est_ftp_link}\ncontains metacharacters (e.g. * or ?). This is not allowed.\n\n"
                abortJob(predictionInstance, mailStr)
                return
            }
            if(estFastaFlag == 1) {
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "The EST/cDNA file was not fasta.")
                String mailStr = "Your AUGUSTUS prediction job ${predictionInstance.accession_id} was aborted because the provided cDNA file\n${predictionInstance.est_ftp_link}\nwas not in DNA fasta format.\n\n"
                abortJob(predictionInstance, mailStr)
                return
            }

            cmd = ["cksum ${dirName}/est.fa"]
            predictionInstance.est_cksum = Utilities.executeForLong(logFile, verb, predictionInstance.accession_id, "estCksumScript", cmd, "(\\d*) \\d* ")
            predictionInstance.est_size =  Utilities.executeForLong(logFile, verb, predictionInstance.accession_id, "estCksumScript", cmd, "\\d* (\\d*) ")
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "est.fa is ${predictionInstance.est_size} big and has a cksum of ${predictionInstance.est_cksum}.")
        } // end of if(!(predictionInstance.est_ftp_link == null))

        // check whether EST file is NOT RNAseq, i.e. does not contain on average very short entries
        int nEntries = 0
        int totalLen = 0
        File estFile = new File(projectDir, "est.fa")
        boolean estExistsFlag = estFile.exists()
        if (estExistsFlag) {
            estFile.eachLine{line ->
                if(line =~ /^>/){
                    nEntries = nEntries + 1
                }else{
                    totalLen = totalLen + line.size()
                }
            }
            if (nEntries == 0) {
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "EST sequence file is not in fasta format. It doesn't contain \">\" characters.")
                String mailStr = "Your AUGUSTUS prediction job ${predictionInstance.accession_id} was aborted because your\ncDNA file is not in fasta format. \n\n"
                abortJob(predictionInstance, mailStr)
                return
            }
            def avEstLen = totalLen/nEntries
            int estMinLen = getEstMinLen()
            int estMaxLen = getEstMaxLen()
            if(avEstLen < estMinLen){
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "EST sequences are on average shorter than ${estMinLen}, suspect RNAseq raw data.")
                String mailStr = "Your AUGUSTUS prediction job ${predictionInstance.accession_id} was aborted because the sequences in your\ncDNA file have an average length of ${avEstLen}. We suspect that sequences files\nwith an average sequence length shorter than ${estMinLen} might contain RNAseq\nraw sequences. Currently, our web server application does not support the integration\nof RNAseq raw sequences. Please either assemble your sequences into longer contigs,\nor remove short sequences from your current file, or submit a new job without\nspecifying a cDNA file.\n\n"
                abortJob(predictionInstance, mailStr)
                return
            }else if(avEstLen > estMaxLen){
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "EST sequences are on average longer than ${estMaxLen}, suspect non EST/cDNA data.")
                String mailStr = "Your AUGUSTUS prediction job ${predictionInstance.accession_id} was aborted because the sequences in your\ncDNA file have an average length of ${avEstLen}. We suspect that sequence\nfiles with an average sequence length longer than ${estMaxLen} might not contain\nESTs or cDNAs. Please either remove long sequences from your current file, or\nsubmit a new job without specifying a cDNA file.\n\n"
                abortJob(predictionInstance, mailStr)
                return
            }
        }

        // confirm file upload via e-mail
        if (predictionInstance.genome_ftp_link != null || predictionInstance.est_ftp_link != null) {
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Retrieved all ftp files successfully.")
            String mailStr = "We have retrieved all files that you specified, successfully. You may delete them\nfrom the public server, now, without affecting the AUGUSTUS prediction job.\n\n"
            predictionInstance.message = "${predictionInstance.message}----------------------------------------\n${new Date()} - Message:\n----------------------------------------\n\n${mailStr}"
            predictionInstance.save(flush: true)
            
            sendMailToUser(predictionInstance, "File upload has been completed for AUGUSTUS prediction job ${predictionInstance.accession_id}", mailStr)
        }

        // File formats appear to be ok.
        // check whether this job was submitted before:
        Closure findPrediction = { Prediction.find { // query returns the first matching result
                project_id          == predictionInstance.project_id && 
                genome_cksum        == predictionInstance.genome_cksum && 
                genome_size         == predictionInstance.genome_size && 
                est_cksum           == predictionInstance.est_cksum && 
                est_size            == predictionInstance.est_size && 
                hint_cksum          == predictionInstance.hint_cksum && 
                hint_size           == predictionInstance.hint_size && 
                archive_cksum       == predictionInstance.archive_cksum && 
                archive_size        == predictionInstance.archive_size && 
                utr                 == predictionInstance.utr &&
                pred_strand         == predictionInstance.pred_strand && 
                alt_transcripts     == predictionInstance.alt_transcripts && 
                allowed_structures  == predictionInstance.allowed_structures && 
                ignore_conflicts    == predictionInstance.ignore_conflicts
                job_status != '6' && // ignore jobs targeted to an identical job
                isNull('old_url') && // ignore jobs targeted to an identical job
                accession_id != predictionInstance.accession_id // not itself
            }
        }
        
        def identicalPrediction = findPrediction()

        if (identicalPrediction != null) {
            //job was submitted before. Send E-Mail to user with a link to the results.
            def oldAccContent = identicalPrediction.accession_id
            // oldID is a parameter that is used for showing redirects (see bottom)
            def oldID = identicalPrediction.id

            String mailStr = "You submitted job ${predictionInstance.accession_id}.\nThe job was aborted because the files that you submitted were submitted, before.\n\n"
            predictionInstance.message = "${predictionInstance.message}----------------------------------------------\n${new Date()} - Error Message:\n----------------------------------------------\n\n${mailStr}"
            predictionInstance.old_url = "${web_output_base_url}prediction/show/${oldID}"
            predictionInstance.save(flush: true)
            
            mailStr += "The old job with identical input files and identical parameters "
            mailStr += "is available at\n${getHttpBaseURL()}show/${oldID}.\n\n"
            
            sendMailToUser(predictionInstance, "AUGUSTUS prediction job ${predictionInstance.accession_id} was submitted before as job ${oldAccContent}", mailStr)
            
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Data are identical to old job ${oldAccContent} with Accession-ID ${oldID}.")
            abortJob(predictionInstance, null, "6")
            
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Web output directory is deleted")
            def cmd = ["rm -r ${web_output_dir}/${predictionInstance.accession_id} &> /dev/null"]
            Utilities.execute(logFile, 2, predictionInstance.accession_id, "removeWeb_output_dir", cmd)
            
            return
        } // end of job was submitted before check

        //rename and move parameters
        // species name for AUGUSTUS
        String species = predictionInstance.project_id
        String cmdStr
        File paramArchFile = new File(projectDir, "parameters.tar.gz")
        if(paramArchFile.exists()){
            def mvParamsScript = new File(projectDir, "mvParams.sh")
            cmdStr = "${AUGUSTUS_SCRIPTS_PATH}/moveParameters.pl ${dirName}/params ${predictionInstance.accession_id} ${AUGUSTUS_SPECIES_PATH} 2> /dev/null\n"
            mvParamsScript << "${cmdStr}"
            Utilities.log(logFile, 3, verb, predictionInstance.accession_id, "mvParamsScript << \"${cmdStr}\"")
            cmdStr = "bash ${mvParamsScript}"
            def mvParamsRunning = "${cmdStr}".execute()
            Utilities.log(logFile, 2, verb, predictionInstance.accession_id, cmdStr)
            mvParamsRunning.waitFor()
            species = "${predictionInstance.accession_id}"
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Moved uploaded parameters and renamed species to ${predictionInstance.accession_id}")
        }
        //Create script:
        Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Writing submission script.")
        File sgeFile = new File(projectDir, "aug-pred.sh")
        // write command in script (according to uploaded files)
        sgeFile << "#!/bin/bash\n#\$ -S /bin/bash\n#\$ -cwd\n\n"
        cmdStr = "mkdir ${dirName}/augustus\n"
        if(estExistsFlag){
            cmdStr = "${cmdStr}blat -noHead ${dirName}/genome.fa ${dirName}/est.fa ${dirName}/est.psl\n"
            cmdStr = "${cmdStr}cat ${dirName}/est.psl | sort -n -k 16,16 | sort -s -k 14,14 > ${dirName}/est.s.psl\n"
            cmdStr = "${cmdStr}${AUGUSTUS_SCRIPTS_PATH}/blat2hints.pl --in=${dirName}/est.s.psl --out=${dirName}/est.hints --source=E\n"
            cmdStr = "${cmdStr}${AUGUSTUS_SCRIPTS_PATH}/blat2gbrowse.pl ${dirName}/est.s.psl ${dirName}/est.gbrowse\n"
        }
        boolean hintExistsFlag = new File(projectDir, "hints.gff").exists()
        if(hintExistsFlag) {
            cmdStr = "${cmdStr}cat ${dirName}/hints.gff >> ${dirName}/est.hints\n"
        }
        
        def radioParameterString = getParameterString(predictionInstance)
        
        if(hintExistsFlag || estExistsFlag) {
            radioParameterString += " --hintsfile=${dirName}/est.hints --extrinsicCfgFile=${AUGUSTUS_CONFIG_PATH}/extrinsic/extrinsic.ME.cfg"
        }
        cmdStr = "${cmdStr}cd ${dirName}/augustus\naugustus --species=${species} ${radioParameterString} ${dirName}/genome.fa --codingseq=on --exonnames=on > ${dirName}/augustus/augustus.gff\n"
        cmdStr = "${cmdStr}${AUGUSTUS_SCRIPTS_PATH}/getAnnoFasta.pl --seqfile=${dirName}/genome.fa ${dirName}/augustus/augustus.gff\n"
        cmdStr = "${cmdStr}cat ${dirName}/augustus/augustus.gff | perl -ne 'if(m/\\tAUGUSTUS\\t/){print;}' > ${dirName}/augustus/augustus.gtf\n"
        cmdStr = "${cmdStr}cat ${dirName}/augustus/augustus.gff | ${AUGUSTUS_SCRIPTS_PATH}/augustus2gbrowse.pl > ${dirName}/augustus/augustus.gbrowse\n"
        cmdStr = "${cmdStr}${AUGUSTUS_SCRIPTS_PATH}/writeResultsPage.pl ${predictionInstance.accession_id} null '${predictionInstance.dateCreated}' ${output_dir} ${web_output_dir} ${AUGUSTUS_CONFIG_PATH} ${AUGUSTUS_SCRIPTS_PATH} 1 2> ${dirName}/writeResults.err"
        sgeFile << "${cmdStr}"
        Utilities.log(logFile, 3, verb, predictionInstance.accession_id, "sgeFile=${cmdStr}")
        
        sgeFile.setExecutable(true, false);
        
        String jobID = JobExecution.getDefaultJobExecution().startJob(dirName, sgeFile.getName(), JobExecution.JobType.PREDICTION, logFile, verb, predictionInstance.accession_id)

        if (jobID == null) {
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "The augustus job wasn't started")
            predictionInstance.results_urls = null
            predictionInstance.job_status = 5
            predictionInstance.save(flush: true)
            return
        }

        predictionInstance.job_id = jobID
        predictionInstance.job_status = 1 // submitted
        predictionInstance.save(flush: true)
        Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Job ${jobID} submitted.")
    }
    
    private String getParameterString(Prediction predictionInstance) {
        String dirName = "${output_dir}/${predictionInstance.accession_id}"
        File projectDir = new File(dirName)
        
        
        String species = predictionInstance.project_id
        boolean overRideUtrFlag = predictionInstance.utr
        
        if (overRideUtrFlag) {
            File archCheckLog = new File(projectDir, "archCheck.log")
            if (archCheckLog.exists()) {
                int archCheckLogSize = archCheckLog.text.size()
                if(archCheckLogSize > 0) {
                    overRideUtrFlag = false // UTR predictions are now permanently disabled
                }
            }
        }
        if (overRideUtrFlag) {
            def utrParamContent = new File("${AUGUSTUS_SPECIES_PATH}/${species}/${species}_utr_probs.pbl")
            if (!utrParamContent.exists()) {
                overRideUtrFlag = false;
            }
        }
        if (overRideUtrFlag && predictionInstance.allowed_structures != 1 && predictionInstance.allowed_structures != 2) {
            overRideUtrFlag = 0;
        }
        
        String radioParameterString = overRideUtrFlag ? " --UTR=on" : " --UTR=off"
        // strand prediction radio buttons
        if (predictionInstance.pred_strand == 1) {
            radioParameterString += " --strand=both"
        }
        else if(predictionInstance.pred_strand == 2) {
            radioParameterString += " --strand=forward"
        }
        else {
            radioParameterString += " --strand=backward"
        }
        // alternative transcript radio buttons
        if(predictionInstance.alt_transcripts == 1) {
            radioParameterString += " --sample=100 --keep_viterbi=true --alternatives-from-sampling=false"
        }
        else if(predictionInstance.alt_transcripts == 2) {
            radioParameterString += " --sample=100 --keep_viterbi=true --alternatives-from-sampling=true --minexonintronprob=0.2 --minmeanexonintronprob=0.5 --maxtracks=2"
        }
        else if(predictionInstance.alt_transcripts == 3) {
            radioParameterString += " --sample=100 --keep_viterbi=true --alternatives-from-sampling=true --minexonintronprob=0.08 --minmeanexonintronprob=0.4 --maxtracks=3"
        }
        else {
            radioParameterString += " --sample=100 --keep_viterbi=true --alternatives-from-sampling=true --minexonintronprob=0.08 --minmeanexonintronprob=0.3 --maxtracks=20"
        }
        // gene structure radio buttons
        if(predictionInstance.allowed_structures == 1) {
            radioParameterString += " --genemodel=partial"
        }
        else if(predictionInstance.allowed_structures == 2) {
            radioParameterString += " --genemodel=complete"
        }
        else if(predictionInstance.allowed_structures == 3) {
            radioParameterString += " --genemodel=atleastone"
        }
        else{
            radioParameterString += " --genemodel=exactlyone"
        }
        // ignore gene structure conflicts with other strand checkbox
        if(predictionInstance.ignore_conflicts) {
            radioParameterString += " --singlestrand=true"
        }    
        
        return radioParameterString
    }
    
    /**
     * Check if the augustus job is still running and set the job_status accordingly
     * 
     * @returns true if the job is done
     */
    @Transactional
    protected boolean checkJobReadyness(AbstractWebAugustusDomainClass instance) {
        Prediction predictionInstance = (Prediction) instance
        String jobID = predictionInstance.job_id
        
        JobExecution.JobStatus status = JobExecution.getDefaultJobExecution().getJobStatus(jobID, logFile, verb, predictionInstance.accession_id)
        
        if (status == null) {
            return false
        }
        else if (JobExecution.JobStatus.WAITING_FOR_EXECUTION.equals(status)) {
            predictionInstance.job_status = '2'
        }
        else if (JobExecution.JobStatus.COMPUTING.equals(status)) {
            if (!predictionInstance.job_status.equals("3")) {
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Job ${jobID} begins running at ${new Date()}.")
            }
            predictionInstance.job_status = '3'
        }
        else { // JobExecution.JobStatus.FINISHED
            return true
        }
        predictionInstance.save(flush: true)
        return false
    }
    
    /**
     * Do all tasks needed to process the job data and cleanup
     */
    @Transactional
    protected void finishJob(AbstractWebAugustusDomainClass instance) {
        Prediction predictionInstance = (Prediction) instance
        Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "finishJob")
        
        String jobID = predictionInstance.job_id
        String dirName = "${output_dir}/${predictionInstance.accession_id}"
        File projectDir = new File(dirName)
        
        int exitCode = JobExecution.getDefaultJobExecution().cleanupJob(dirName, this, JobExecution.JobType.PREDICTION, logFile, verb, predictionInstance.accession_id)
        if (exitCode != 0) {
            // try again - perhaps a ssh connection was cut
            Utilities.log(logFile, 1, verb, "SEVERE", predictionInstance.accession_id, "cleanupJob failed. exitCode=${exitCode} try again.")
            sleep(10000)
            exitCode = JobExecution.getDefaultJobExecution().cleanupJob(dirName, this, JobExecution.JobType.PREDICTION, logFile, verb, predictionInstance.accession_id)
            if (exitCode != 0) {
                Utilities.log(logFile, 1, verb, "SEVERE", predictionInstance.accession_id, "cleanupJob failed again. exitCode=${exitCode} try again.")
            }
        }
        
        // set file rigths to readable by others
        Utilities.log(logFile, 3, verb, predictionInstance.accession_id, "set file permissions on ${web_output_dir}/${predictionInstance.accession_id}")
        def webOutputDir = new File(web_output_dir, predictionInstance.accession_id) 
        if (webOutputDir.exists()) {
            webOutputDir.setReadable(true, false)
            webOutputDir.setExecutable(true, false);
            webOutputDir.eachFile { file -> file.setReadable(true, false) } // actually just predictions.tar.gz
        }
        // collect results link information
        if(new File("${web_output_dir}/${predictionInstance.accession_id}/predictions.tar.gz").exists()){
            predictionInstance.results_urls = "<p><b>Prediction archive</b>&nbsp;&nbsp;<a href=\"${web_output_url}${predictionInstance.accession_id}/predictions.tar.gz\">predictions.tar.gz</a><br></p>"
            predictionInstance.save(flush: true)
        }
        // check whether errors occured by log-file-sizes
        def sgeErrSize
        def writeResultsErrSize
        if (exitCode != 0) {
            sgeErrSize = 10
            Utilities.log(logFile, 1, verb, "SEVERE", predictionInstance.accession_id, "cleanupJob failed. Setting size to default value 10.")
        }
        else {
            if(new File(projectDir, "aug-pred.sh.e${jobID}").exists()){
                sgeErrSize = new File(projectDir, "aug-pred.sh.e${jobID}").size()
            }else{
                sgeErrSize = 10
                Utilities.log(logFile, 1, verb, "SEVERE", predictionInstance.accession_id, "segErrFile was not created. Setting size to default value 10.")
            }
        }
        if(new File(projectDir, "writeResults.err").exists()){
            writeResultsErrSize = new File(projectDir, "writeResults.err").size()
        }else{
            writeResultsErrSize = 10
            Utilities.log(logFile, 1, verb, "SEVERE", predictionInstance.accession_id, "writeResultsErr was not created. Setting size to default value 10.")
        }

        String admin_email = getAdminEmailAdress()
        String footer = getEmailFooter()
        
        if(sgeErrSize==0 && writeResultsErrSize==0){
            String mailStr = "Your AUGUSTUS prediction job ${predictionInstance.accession_id} finished.\n\n"
            predictionInstance.message = "${predictionInstance.message}----------------------------------------\n${new Date()} - Message:\n----------------------------------------\n\n${mailStr}"
            predictionInstance.job_status = 4
            predictionInstance.save(flush: true)
            
            if(predictionInstance.email_adress == null){
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Computation was successful. Did not send e-mail to user because no e-mail adress was supplied.")
            }
            else {
                String msgStr = "${mailStr}You find the results at "
                msgStr += "${getHttpBaseURL()}show/${predictionInstance.id}.\n\n"
                sendMailToUser(predictionInstance, "AUGUSTUS prediction job ${predictionInstance.accession_id} is complete", msgStr)
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Sent confirmation Mail that job computation was successful.")
            }
            
            def packResults = new File("${output_dir}/pack${predictionInstance.accession_id}.sh")
            String cmdStr = "cd ${output_dir}; tar -czvf ${predictionInstance.accession_id}.tar.gz ${predictionInstance.accession_id} &> /dev/null"
            packResults << "${cmdStr}"
            Utilities.log(logFile, 3, verb, predictionInstance.accession_id, "packResults << \"${cmdStr}\"")
            cmdStr = "bash ${output_dir}/pack${predictionInstance.accession_id}.sh"
            def cleanUp = "${cmdStr}".execute()
            Utilities.log(logFile, 2, verb, predictionInstance.accession_id, cmdStr)
            cleanUp.waitFor()
            cmdStr = "rm ${output_dir}/pack${predictionInstance.accession_id}.sh &> /dev/null"
            cleanUp = "${cmdStr}".execute()
            Utilities.log(logFile, 2, verb, predictionInstance.accession_id, cmdStr)
            deleteDir(predictionInstance)
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "job directory was packed with tar/gz.")
            Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Job completed. Result: ok.")
        }else{
            String msgStr = "Hi ${admin_email}!\n\nJob: ${predictionInstance.accession_id}\n"
            msgStr += "Link: ${getHttpBaseURL()}show/${predictionInstance.id}\n\n"
            if(sgeErrSize > 0){
                String computeClusterName = JobExecution.getDefaultJobExecution().getName().trim()
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "a ${computeClusterName} error occured!");
                msgStr += "A ${computeClusterName} error occured. Please check manually what's wrong.\n"
            }else{
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "an error occured during writing results!");
                msgStr += "An error occured during writing results.. Please check manually what's wrong.\n"
            }
            msgStr += "The user has "
            if (predictionInstance.email_adress == null) {
                msgStr += "not "
            }
            msgStr += "been informed."
                
            sendMail {
                to "${admin_email}"
                subject "Error in AUGUSTUS prediction job ${predictionInstance.accession_id}"
                text """${msgStr}${footer}"""
            }
            
            String mailStr = "An error occured while running the AUGUSTUS prediction job ${predictionInstance.accession_id}.\n\n"
            
            String senderAdress = PredictionService.getWebaugustusEmailAdress()
            predictionInstance.message = "${predictionInstance.message}----------------------------------------------\n${new Date()} - Error Message:\n----------------------------------------------\n\n${mailStr}Please contact ${senderAdress} if you want to find out what went wrong.\n\n"
            if(predictionInstance.email_adress == null){
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "The job is in an error state. Could not send e-mail to anonymous user because no email adress was supplied.")
            }else{
                msgStr = "${mailStr}The administrator of the AUGUSTUS web server has been informed and"
                msgStr += " will get back to you as soon as the problem is solved.\n\n"
                sendMailToUser(predictionInstance, "An error occured while executing AUGUSTUS prediction job ${predictionInstance.accession_id}", msgStr)
                Utilities.log(logFile, 1, verb, predictionInstance.accession_id, "Sent confirmation Mail, the job is in an error state.")
            }
            predictionInstance.job_status = 5
            predictionInstance.save(flush: true)
        }
    }
    
}