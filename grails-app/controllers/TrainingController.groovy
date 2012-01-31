// The class TrainingController controls everything that is related to submitting a job for training AUGUSTUS  through the webserver:
//    - it handles the file upload (or wget)
//    - format check
//    - SGE job submission and status checks
//    - rendering of results/job status page
//    - sending E-Mails concerning the job status (submission, errors, finished)

import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.Reader
import java.io.StreamTokenizer
import java.io.StringWriter
import java.io.Writer
import java.net.URL
import java.util.zip.GZIPInputStream
import java.net.UnknownHostException
import java.net.HttpURLConnection;

class TrainingController {
	// need to adjust the output dir to whatever working dir! This is where uploaded files and results will be saved.
	def output_dir = "/data/www/augtrain/webdata" // should be something in home of webserver user and augustus frontend user.
//	def output_dir = "/data/www/test"
	// this log File contains the "process log", what was happening with which job when.
	def logFile = new File("${output_dir}/train.log")
	// this log File contains the "database" (not identical with the grails database and simply for logging purpose)
	def dbFile = new File("${output_dir}/augustus-database.log")
	// oldID is a parameter that is used for show redirects (see bottom)
	def oldID
	def oldAccID
	// web-output, root directory to the results that are shown to end users
	def web_output_dir = "/var/www/trainaugustus/training-results" // must be writable to webserver application
//	def web_output_dir = "/var/www/test"
	// AUGUSTUS_CONFIG_PATH
	def AUGUSTUS_CONFIG_PATH = "/usr/local/augustus/trunks/config";
	def AUGUSTUS_SCRIPTS_PATH = "/usr/local/augustus/trunks/scripts";
	def scaffold = Training
	// Admin mail for errors
	def admin_email = "katharina.hoff@gmail.com"
	// sgeLen length of SGE queue, when is reached "the server is buisy" will be displayed
	def sgeLen = 20;
	// max button filesize
	def int maxButtonFileSize = 104857600 // 100 MB = 13107200 bytes = 104857600 bit, getFile etc. gives size in bit
	def preUploadSize
	// max ftp/http filesize
	def int maxFileSizeByWget = 1073741824 // 1 GB = 1073741824 bytes, curl gives size in bytes
	// EST sequence properties (length)
	def int estMinLen = 250
	def int estMaxLen = 20000
	// logging verbosity-level
	def verb = 2 // 1 only basic log messages, 2 all issued commands, 3 also script content
	def cmd2Script
	def cmdStr

	def logDate

	// human verification:
	def simpleCaptchaService

	// check whether the server is buisy
	def beforeInterceptor = {
		def String prefixChars ="ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890_"
		def rnd = new Random()
		def qstatFilePrefix = (1..10).sum{prefixChars[ rnd.nextInt(prefixChars.length()) ]} 
		def qstatFile = new File("${output_dir}/${qstatFilePrefix}.qstatScript")
		cmd2Script = "/usr/bin/qstat -u \"*\" | grep qw | wc -l > ${output_dir}/${qstatFilePrefix}.qstatResult 2> /dev/null"
		qstatFile << "${cmd2Script}"
		if(verb > 2){
			logDate = new Date()
			logFile <<  "${logDate} SGE          v3 - qstatFile << \"${cmd2Script}\"\n"
		}
		if(!qstatFile.exists()){
			logDate = new Date()
			logFile << "SEVERE ${logDate} SGE          v1 - ${qstatFile} does not exist!\n"
		}
		cmdStr = "bash ${output_dir}/${qstatFilePrefix}.qstatScript"
		def qstatStatus = "${cmdStr}".execute()
		if(verb > 2){
			logDate = new Date()
			logFile <<  "${logDate} SGE          v3 - \"${cmdStr}\"\n"
		}
		qstatStatus.waitFor()
		def qstatStatusResult = new File("${output_dir}/${qstatFilePrefix}.qstatResult").text
		def qstatStatus_array = qstatStatusResult =~ /(\d*)/
		def qstatStatusNumber 
		(1..qstatStatus_array.groupCount()).each{qstatStatusNumber = "${qstatStatus_array[0][it]}"}
		cmdStr = "rm -r ${output_dir}/${qstatFilePrefix}.qstatScript &> /dev/null"
		def delProc = "${cmdStr}".execute()
		if(qstatFile.exists()){
			//logDate = new Date()
			//logFile << "SEVERE ${logDate} SGE          v1 - ${qstatFile} was not deleted!\n"
		}
		if(verb > 2){
			logDate = new Date()
			logFile <<  "${logDate} SGE          v3 - \"${cmdStr}\"\n"
		}
		delProc.waitFor()
		cmdStr = "rm -r ${output_dir}/${qstatFilePrefix}.qstatResult &> /dev/null"
		delProc = "${cmdStr}".execute()
		if(verb > 2){
			logDate = new Date()
			logFile <<  "${logDate} SGE          v3 - \"${cmdStr}\"\n"
		}
		delProc.waitFor()

		if(qstatStatusNumber > sgeLen){
			// get date
			def todayTried = new Date()
			// get IP-address
			String userIPTried = request.remoteAddr
			logDate = new Date()
			logFile <<  "${logDate} SGE          v1 - On ${todayTried} somebody with IP ${userIPTried} tried to invoke the Training webserver but the SGE queue was longer than ${sgeLen} and the user was informed that submission is currently not possible\n"
			render "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\"/><meta name=\"layout\" content=\"main\" /><title>Submitt Training</title><script type=\"text/javascript\" src=\"js/md_stylechanger.js\"></script></head><body><!-- Start: Kopfbereich --><p class=\"unsichtbar\"><a href=\"#inhalt\" title=\"Directly to Contents\">Directly to Contents</a></p><div id=\"navigation_oben\"><a name=\"seitenanfang\"></a><table width=\"100%\" border=\"0\" cellpadding=\"0\" cellspacing=\"1\"><tr><td nowrap=\"nowrap\"><a href=\"http://www.uni-greifswald.de\" target=\"_blank\" class=\"mainleveltop_\" >University of Greifswald</a><span class=\"mainleveltop_\">&nbsp;|&nbsp; </span><a href=\"http://www.mnf.uni-greifswald.de/\" target=\"_blank\" class=\"mainleveltop_\" >Faculty</a><span class=\"mainleveltop_\">&nbsp;|&nbsp; </span><a href=\"http://www.math-inf.uni-greifswald.de/\" target=\"_blank\" class=\"mainleveltop_\" >Institute</a><span class=\"mainleveltop_\">&nbsp;|&nbsp;</span><a href=\"http://bioinf.uni-greifswald.de/\" target=\"_blank\" class=\"mainleveltop_\">Bioinformatics Group</a></td></tr></table></div><div id=\"banner\"><div id=\"banner_links\"><a href=\"http://www.math-inf.uni-greifswald.de/mathe/index.php\" title=\"Institut f&uuml;r Mathematik und Informatik\"><img src=\"../images/header.gif\" alt=\"Directly to home\" /> </a></div><div id=\"banner_mitte\"><div id=\"bannertitel1\">Bioinformatics Web Server at University of Greifswald</div><div id=\"bannertitel2\">Gene Prediction with AUGUSTUS</div></div><div id=\"banner_rechts\"><a href=\"http://www.math-inf.uni-greifswald.de/mathe/index.php/geschichte-und-kultur/167\" title=\"Voderberg-Doppelspirale\"><img src=\"../images/spirale.gif\" align=\"left\" /></a></div></div><div id=\"wegweiser\">Navigation for: &nbsp; &nbsp;<span class=\"breadcrumbs pathway\">Submitt Training</span><div class=\"beendeFluss\"></div></div><!-- Ende: Kopfbereich --><!-- Start: Koerper --><div id=\"koerper\"><div id=\"linke_spalte\"><ul class=\"menu\"><li><a href=\"../index.gsp\"><span>Introduction</span></a></li><li><a href=\"/augustus-training/training/create\"><span>Submitt Training</span></a></li><li><a href=\"/augustus-training/prediction/create\"><span>Submitt Prediction</span></a></li><li><a href=\"../help.gsp\"><span>Help</span></a></li><li><a href=\"../references.gsp\"><span>Links & References</span></a></li><li><a href=\"http://bioinf.uni-greifswald.de\"><span>Bioinformatics Group</span></a></li><li><a href=\"http://bioinf.uni-greifswald.de/bioinf/impressum.html\"><span>Impressum</span></a></li></ul></div><div id=\"mittel_spalte\"><div class=\"main\" id=\"main\"><h1><font color=\"#006699\">The Server is Busy</font></h1><p>You tried to access the AUGUSTUS training job submission page.</p><p>Training parameters for gene prediction is a process that takes a lot of computation time. We estimate that one training process requires approximately 10 days. Our web server is able to process a certain number of jobs in parallel, and we established a waiting queue. The waiting queue has a limited length, though. Currently, all slots for computation and for waiting are occupied.</p><p>We apologize for the inconvenience! Please try to submitt your job in a couple of weeks, again.</p><p>Feel free to contact us in case your job is particularly urgent.</p></div><p>&nbsp;</p>           </div><div id=\"rechte_spalte\"><div class=\"linien_div\"><h5 class=\"ueberschrift_spezial\">CONTACT</h5><strong>Institute for Mathematics und Computer Sciences</strong><br/><strong>Bioinformatics Group</strong><br />Walther-Rathenau-Stra&szlig;e 47<br />17487 Greifswald<br />Germany<br />Tel.: +49 (0)3834 86 - 46 24<br/>Fax:  +49 (0)3834 86 - 46 40<br /><br /><a href=\"mailto:augustus-web@uni-greifswald.de\" title=\"E-Mail augustus-web@uni-greifswald.de, opens the standard mail program\">augustus-web@uni-greifswald.de</a></div></div><div class=\"beendeFluss\"></div></div><!-- Ende: Koerper --><!-- Start: Fuss --><div id=\"fuss\"><div id=\"fuss_links\"><p class=\"copyright\">&copy; 2011 University of Greifswald</p></div><div id=\"fuss_mitte\"><div class=\"bannergroup\"></div></div><div id=\"fuss_rechts\" ><ul><li><a href=\"#seitenanfang\"><img hspace=\"5\" height=\"4\" border=\"0\" width=\"7\" alt=\"Seitenanfang\" src=\"../images/top.gif\" />Top of page</a></li></ul></div><div class=\"beendeFluss\"></div></div><!-- Ende: Fuss --></body></html>"
			return
		}		
	} 

	// the method commit is started if the "Submit Job" button on the website is hit. It is the main method of Training Controller and contains a Thread method that will continue running as a background process after the user is redirected to the job status page.
	def commit = {
		def trainingInstance = new Training(params)
		if(!(trainingInstance.id == null)){
         		redirect(action:create, params:[email_adress:"${trainingInstance.email_adress}", project_name:"${trainingInstance.project_name}"])
			return
		}else{
			// info string for confirmation E-Mail
			def confirmationString
			confirmationString = "Training job ID: ${trainingInstance.accession_id}\n"
			confirmationString = "${confirmationString}Species name: ${trainingInstance.project_name}\n"
			def emailStr
			trainingInstance.job_id = 0
			trainingInstance.job_error = 0
			// define flags for file format check, file removal in case of failure
			def genomeFastaFlag = 0
			def estFastaFlag = 0
			def estExistsFlag = 0
			def structureGffFlag = 0
			def structureGbkFlag = 0
			def structureExistsFlag = 0
			def proteinFastaFlag = 0
			def proteinExistsFlag = 0
			def metacharacterFlag = 0
			// delProc is needed at many places
			def delProc
			def st
			def content
			def int error_code
			def urlExistsScript
			def autoAugErrSize = 10 // default: error
			def sgeErrSize = 10 // default: error
			def writeResultsErrSize = 10 // default: error
			// get date
			def today = new Date()
			logDate = new Date()
			logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - AUGUSTUS training webserver starting on ${today}\n"
			// get IP-address
			String userIP = request.remoteAddr
			logDate = new Date()
			logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - user IP: ${userIP}\n"

			//verify that the submitter is a person
			boolean captchaValid = simpleCaptchaService.validateCaptcha(params.captcha)
			if(captchaValid == false){
				logDate = new Date()
				logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - The user is probably not a human person. Job aborted.\n"
				flash.error = "The verification string at the bottom of the page was not entered correctly!"
            			redirect(action:create, params:[email_adress:"${trainingInstance.email_adress}"])
           			return
			}
			
			// check that species name does not contain spaces
			if(trainingInstance.project_name =~ /\s/){
				logDate = new Date()
				logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - The species name contained whitespaces.\n"
				logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - Job ${trainingInstance.accession_id} by user ${trainingInstance.email_adress} is aborted!\n"
			       flash.error = "Species name  ${trainingInstance.project_name} contains white spaces."
   	  		       redirect(action:create, params:[email_adress:"${trainingInstance.email_adress}"])
	  		       return
			} 

			// upload of genome file
			def uploadedGenomeFile
			uploadedGenomeFile = request.getFile('GenomeFile')
			// check file size
		        preUploadSize = uploadedGenomeFile.getSize()
			def seqNames = []
			def String dirName = "${output_dir}/${trainingInstance.accession_id}"
			def projectDir = new File(dirName)
			if(!uploadedGenomeFile.empty){
				if(preUploadSize <= maxButtonFileSize){
					projectDir.mkdirs()
					uploadedGenomeFile.transferTo( new File (projectDir, "genome.fa"))
					trainingInstance.genome_file = uploadedGenomeFile.originalFilename
					confirmationString = "${confirmationString}Genome file: ${trainingInstance.genome_file}\n"
					if("${uploadedGenomeFile.originalFilename}" =~ /\.gz/){
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - Genome file is gzipped.\n"
						def gunzipGenomeScript = new File("${projectDir}/gunzipGenome.sh")
						gunzipGenomeScript << "cd ${projectDir}; mv genome.fa genome.fa.gz &> /dev/null; gunzip genome.fa.gz 2> /dev/null"
						def gunzipGenome = "bash ${gunzipGenomeScript}".execute()
						gunzipGenome.waitFor()
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - Unpacked genome file.\n"
						cmdStr = "rm ${gunzipGenomeScript} &> /dev/null"
						delProc = "${cmdStr}".execute()
						if(verb > 1){
							logDate = new Date()
							logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
						}
						delProc.waitFor()
					}
					logDate = new Date()
					logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - uploaded genome file ${uploadedGenomeFile.originalFilename} was renamed to genome.fa and moved to ${projectDir}\n"
					// check for fasta format & extract fasta headers for gff validation:
					new File("${projectDir}/genome.fa").eachLine{line -> 
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
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - The genome file contains metacharacters (e.g. * or ?). ${projectDir} is deleted.\n";
						cmdStr = "rm -r ${projectDir} &> /dev/null"
						delProc = "${cmdStr}".execute()
						if(verb > 1){
							logDate = new Date()
							logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
						}	
						delProc.waitFor()
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - Job ${trainingInstance.accession_id} by user ${trainingInstance.email_adress} is aborted!\n"
						flash.error = "The genome file contains metacharacters (*, ?, ...). This is not allowed."
						redirect(action:create, params:[email_adress:"${trainingInstance.email_adress}", project_name:"${trainingInstance.project_name}"])
						return
					}


					if(genomeFastaFlag == 1) {
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - The genome file was not fasta. Project directory ${projectDir} is deleted.\n"
						cmdStr = "rm -r ${projectDir} &> /dev/null"
						delProc = "${cmdStr}".execute()
						if(verb > 1){
							logDate = new Date()
							logFile << "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
						}
						delProc.waitFor()
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - Job ${trainingInstance.accession_id} by user ${trainingInstance.email_adress} is aborted!\n"
						flash.error = "Genome file ${uploadedGenomeFile.originalFilename} is not in DNA fasta format."
						redirect(action:create, params:[email_adress:"${trainingInstance.email_adress}", project_name:"${trainingInstance.project_name}"])
						return
					} else {
						def genomeCksumScript = new File("${projectDir}/genome_cksum.sh")
						def genomeCksumFile = "${projectDir}/genome.cksum"
						cmd2Script = "cksum ${projectDir}/genome.fa > ${genomeCksumFile} 2> /dev/null"
						genomeCksumScript << "${cmd2Script}"
						if(verb > 2){
							logDate = new Date()
							logFile <<  "${logDate} ${trainingInstance.accession_id} v3 - genomeCksumScript << \"${cmd2Script}\"\n"
						}
						cmdStr = "bash ${projectDir}/genome_cksum.sh"
						def genomeCksumProcess = "${cmdStr}".execute()
						if(verb > 1){
							logDate = new Date()
							logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
						}
						genomeCksumProcess.waitFor()
						def genomeCksumContent = new File("${genomeCksumFile}").text
						def genomeCksum_array = genomeCksumContent =~/(\d*) \d* /
						def genomeCksum
						(1..genomeCksum_array.groupCount()).each{genomeCksum = "${genomeCksum_array[0][it]}"}
						trainingInstance.genome_cksum = "${genomeCksum}"
						trainingInstance.genome_size = uploadedGenomeFile.size
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - genome.fa is ${trainingInstance.genome_size} big and has a cksum of ${genomeCksum}.\n"
						cmdStr = "rm ${projectDir}/genome.cksum &> /dev/null"
						delProc = "${cmdStr}".execute()
						if(verb > 1){
							logDate = new Date()
							logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
						}
						delProc.waitFor()
						cmdStr = "rm ${projectDir}/genome_cksum.sh &> /dev/null"
						delProc = "${cmdStr}".execute()
						if(verb > 1){
							logDate = new Date()
							logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
						}
						delProc.waitFor()
					}
				}else{
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - The selected genome file was bigger than ${maxButtonFileSize}. Submission rejected.\n"
						flash.error = "Genome file is bigger than ${maxButtonFileSize} bytes, which is our maximal size for file upload from local harddrives via web browser. Please select a smaller file or use the ftp/http web link file upload option."
						redirect(action:create, params:[email_adress:"${trainingInstance.email_adress}", project_name:"${trainingInstance.project_name}"])
						return
				}	
			} // end of genome file upload

			// retrieve beginning of genome file for format check
			if(!(trainingInstance.genome_ftp_link == null)){
				logDate = new Date()
				logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - genome web-link is ${trainingInstance.genome_ftp_link}\n"
				projectDir.mkdirs()
				def URL url = new URL("${trainingInstance.genome_ftp_link}");
				// check whether URL exists
				urlExistsScript = new File("${projectDir}/genomeExists.sh")
				cmd2Script = "curl -o /dev/null --silent --head --write-out '%{http_code}\n' \"${trainingInstance.genome_ftp_link}\" > ${projectDir}/genomeExists"
				urlExistsScript << "${cmd2Script}"
				if(verb > 2){
					logDate = new Date()
					logFile <<  "${logDate} ${trainingInstance.accession_id} v3 - urlExistsScript << \"${cmd2Script}\"\n"
				}
				cmdStr = "bash ${urlExistsScript}"
				def genomeUrlExists = "${cmdStr}".execute()
				if(verb > 1){
					logDate = new Date()
					logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
				}
				genomeUrlExists.waitFor()
				cmdStr = "rm ${urlExistsScript} &> /dev/null"			
				delProc = "${cmdStr}".execute()
				if(verb > 1){
					logDate = new Date()
					logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
				}
				delProc.waitFor()
				content = new File("${projectDir}/genomeExists").text
				st = new Scanner(content)//works for exactly one number in a file
				error_code = st.nextInt();
				if(!(error_code == 200)){
					logDate = new Date()
					logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - The genome URL is not accessible. Response code: ${error_code}. Aborting job.\n"
					cmdStr = "rm -r ${projectDir} &> /dev/null"
					delProc = "${cmdStr}".execute()
					if(verb > 1){
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
					}
					delProc.waitFor()
					flash.error = "Cannot retrieve genome file from HTTP/FTP link ${trainingInstance.genome_ftp_link}."
					redirect(action:create, params:[email_adress:"${trainingInstance.email_adress}", project_name:"${trainingInstance.project_name}"])
					return
				}else{
					logDate = new Date()
					logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - The genome URL is accessible. Response code: ${error_code}.\n"
				}
				// checking web file for DNA fasta format: 
				def URLConnection uc = url .openConnection()
				confirmationString = "${confirmationString}Genome file: ${trainingInstance.genome_ftp_link}\n"
				if(!("${trainingInstance.genome_ftp_link}" =~ /\.gz/)){
					def BufferedReader br = new BufferedReader(new InputStreamReader(uc.getInputStream()))
					try{
						def String inputLine=null
						def lineCounter = 1;
						while ( ((inputLine = br.readLine()) != null) && (lineCounter <= 20)) {
							if(!(inputLine =~ /^[>AaTtGgCcHhXxRrYyWwSsMmKkBbVvDdNn]/) && !(inputLine =~ /^$/)){ genomeFastaFlag = 1 }
								lineCounter = lineCounter + 1
						}
					}finally{
						br.close()
					}
					if(genomeFastaFlag == 1) {
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - The first 20 lines in genome file are not fasta.\n"
						cmdStr = "rm -r ${projectDir} &> /dev/null"
						delProc = "${cmdStr}".execute()
						if(verb > 1){
							logDate = new Date()
							logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
						}
						delProc.waitFor()
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - Project directory ${projectDir} is deleted.\n${trainingInstance.accession_id} v1 - Job ${trainingInstance.accession_id} by user ${trainingInstance.email_adress} is aborted!\n"
						flash.error = "Genome file ${trainingInstance.genome_ftp_link} is not in DNA fasta format."
						redirect(action:create, params:[email_adress:"${trainingInstance.email_adress}", project_name:"${trainingInstance.project_name}"])
						return
					}
				}else{
					logDate = new Date()
					logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - The linked genome file is gzipped. Format will be checked later after extraction.\n"
				}
			}
 
			// upload of est file
			def uploadedEstFile = request.getFile('EstFile')
			// check file size
			preUploadSize = uploadedEstFile.getSize()
			if(!uploadedEstFile.empty){
				if(preUploadSize <= maxButtonFileSize){
					projectDir.mkdirs()
					uploadedEstFile.transferTo( new File (projectDir, "est.fa"))
					trainingInstance.est_file = uploadedEstFile.originalFilename
					confirmationString = "${confirmationString}cDNA file: ${trainingInstance.est_file}\n"
					if("${uploadedEstFile.originalFilename}" =~ /\.gz/){
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - EST file is gzipped.\n"
						def gunzipEstScript = new File("${projectDir}/gunzipEst.sh")
						cmd2Script = "cd ${projectDir};\n mv est.fa est.fa.gz &> /dev/null;\n gunzip est.fa.gz 2> /dev/null"
						gunzipEstScript << "${cmd2Script}"
						if(verb > 2){
							logDate = new Date()
							logFile <<  "${logDate} ${trainingInstance.accession_id} v3 - gunzipEstScript << \"${cmd2Script}\"\n"
						}
						cmdStr = "bash ${projectDir}/gunzipEst.sh"
						def gunzipEst = "${cmdStr}".execute()
						if(verb > 1){
							logDate = new Date()
							logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
						}
						gunzipEst.waitFor()
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - Unpacked EST file.\n"
						cmdStr = "rm ${projectDir}/gunzipEst.sh &> /dev/null"
						delProc = "${cmdStr}".execute()
						if(verb > 1){
							logDate = new Date()
							logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
						}
						delProc.waitFor()
					}
					logDate = new Date()
					logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - Uploaded EST file ${uploadedEstFile.originalFilename} was renamed to est.fa and moved to ${projectDir}\n"
					// check fasta format
					new File("${projectDir}/est.fa").eachLine{line -> 
						if(line =~ /\*/ || line =~ /\?/){
							metacharacterFlag = 1
						}else{
							if(!(line =~ /^[>AaTtGgCcHhXxRrYyWwSsMmKkBbVvDdNnUu]/) && !(line =~ /^$/)){ 
								estFastaFlag = 1
							}
						}
					}
					if(metacharacterFlag == 1){
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - The cDNA file contains metacharacters (e.g. * or ?). ${projectDir} is deleted.\n";
						cmdStr = "rm -r ${projectDir} &> /dev/null"
						delProc = "${cmdStr}".execute()
						if(verb > 1){
							logDate = new Date()
							logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
						}
						delProc.waitFor()
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - Job ${trainingInstance.accession_id} by user ${trainingInstance.email_adress} is aborted!\n"
						flash.error = "The cDNA file contains metacharacters (*, ?, ...). This is not allowed."
						redirect(action:create, params:[email_adress:"${trainingInstance.email_adress}", project_name:"${trainingInstance.project_name}"])
						return
					}
					if(estFastaFlag == 1) {
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - The cDNA file was not fasta. ${projectDir} is deleted.\n"
						cmdStr = "rm -r ${projectDir} &> /dev/null"
						delProc = "${cmdStr}".execute()
						if(verb > 1){
							logDate = new Date()
							logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
						}
						delProc.waitFor()
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - Job ${trainingInstance.accession_id} by user ${trainingInstance.email_adress} is aborted!\n"
						flash.error = "cDNA file ${uploadedEstFile.originalFilename} is not in DNA fasta format."
						redirect(action:create, params:[email_adress:"${trainingInstance.email_adress}", project_name:"${trainingInstance.project_name}"])
						return
					} else { estExistsFlag = 1 }
					def estCksumScript = new File("${projectDir}/est_cksum.sh")
					def estCksumFile = "${projectDir}/est.cksum"
					cmd2Script = "cksum ${projectDir}/est.fa > ${estCksumFile} 2> /dev/null"
					estCksumScript << "${cmd2Script}"
						if(verb > 2){
							logDate = new Date()
							logFile <<  "${logDate} ${trainingInstance.accession_id} v3 - estCksumScript << \"${cmd2Script}\"\n"
						}
					cmdStr = "bash ${projectDir}/est_cksum.sh"
					def estCksumProcess = "${cmdStr}".execute()
						if(verb > 1){
							logDate = new Date()
							logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
						}
					estCksumProcess.waitFor()
					def estCksumContent = new File("${estCksumFile}").text
					def estCksum_array = estCksumContent =~/(\d*) \d* /
					def estCksum
					(1..estCksum_array.groupCount()).each{estCksum = "${estCksum_array[0][it]}"}
					trainingInstance.est_cksum = "${estCksum}"
					trainingInstance.est_size = uploadedEstFile.size
					logDate = new Date()
					logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - est.fa is ${trainingInstance.est_size} big and has a cksum of ${estCksum}.\n"
					cmdStr = "rm ${projectDir}/est.cksum &> /dev/null"
					delProc = "${cmdStr}".execute()
					if(verb > 1){
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
					}
					delProc.waitFor()
					cmdStr = "rm ${projectDir}/est_cksum.sh &> /dev/null"
					delProc = "${cmdStr}".execute()
					if(verb > 1){
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
					}
					delProc.waitFor()
				}else{
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - The selected cDNA file was bigger than ${maxButtonFileSize}. Submission rejected.\n"
						flash.error = "cDNA file is bigger than ${maxButtonFileSize} bytes, which is our maximal size for file upload from local harddrives via web browser. Please select a smaller file or use the ftp/http web link file upload option."
						redirect(action:create, params:[email_adress:"${trainingInstance.email_adress}", project_name:"${trainingInstance.project_name}"])
						return
				}
			}

			// retrieve beginning of est file for format check
			if(!(trainingInstance.est_ftp_link == null)){
				logDate = new Date()
				logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - est web-link is ${trainingInstance.est_ftp_link}\n"
				projectDir.mkdirs()
				confirmationString = "${confirmationString}cDNA file: ${trainingInstance.est_ftp_link}\n"
				if(!("${trainingInstance.est_ftp_link}" =~ /\.gz/)){
					def URL url = new URL("${trainingInstance.est_ftp_link}");
					// check whether URL exists
					urlExistsScript = new File("${projectDir}/estExists.sh")
					cmd2Script = "curl -o /dev/null --silent --head --write-out '%{http_code}\n' \"${trainingInstance.est_ftp_link}\" > ${projectDir}/estExists"
					urlExistsScript << "${cmd2Script}"
					if(verb > 2){
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v3 - urlExistsScript << \"${cmd2Script}\"\n"
					}
					cmdStr = "bash ${urlExistsScript}"
					def genomeUrlExists = "${cmdStr}".execute()
					if(verb > 1){
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
					}
					genomeUrlExists.waitFor()
					cmdStr = "rm ${urlExistsScript} &> /dev/null"			
					delProc = "${cmdStr}".execute()
					if(verb > 1){
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
					}
					delProc.waitFor()
					content = new File("${projectDir}/estExists").text
					st = new Scanner(content)//works for exactly one number in a file
					error_code = st.nextInt();
					if(!(error_code == 200)){
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - The EST URL is not accessible. Response code: ${error_code}. Aborting job.\n"
						cmdStr = "rm -r ${projectDir} &> /dev/null"
						delProc = "${cmdStr}".execute()
						if(verb > 1){
							logDate = new Date()
							logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
						}
						delProc.waitFor()
						flash.error = "Cannot retrieve cDNA file from HTTP/FTP link ${trainingInstance.est_ftp_link}."
						redirect(action:create, params:[email_adress:"${trainingInstance.email_adress}", project_name:"${trainingInstance.project_name}"])
						return
					}else{
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - The EST URL is accessible. Response code: ${error_code}.\n"
					}
					// checking web file for DNA fasta format: 
					def URLConnection uc = url .openConnection()
					def BufferedReader br = new BufferedReader(new InputStreamReader(uc.getInputStream()))
					try{
						def String inputLine=null
						def lineCounter = 1
						while ( ((inputLine = br.readLine()) != null) && (lineCounter <= 20)) {
							if(!(inputLine =~ /^[>AaTtGgCcHhXxRrYyWwSsMmKkBbVvDdNnUu]/) && !(inputLine =~ /^$/)){ estFastaFlag = 1 }
							lineCounter = lineCounter + 1
						}
					}finally{
						br.close()
					}
					if(estFastaFlag == 1) {
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - The cDNA file was not fasta. ${projectDir} is deleted.\n"
						cmdStr = "rm -r ${projectDir} &> /dev/null"
						delProc = "${cmdStr}".execute()
						if(verb > 1){
							logDate = new Date()
							logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
						}
						delProc.waitFor()
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - Job ${trainingInstance.accession_id} by user ${trainingInstance.email_adress} is aborted!\n"
						flash.error = "cDNA file ${trainingInstance.est_ftp_link} is not in DNA fasta format."
						redirect(action:create, params:[email_adress:"${trainingInstance.email_adress}", project_name:"${trainingInstance.project_name}"])
						return
					}
				}else{
					logDate = new Date()
					logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - The linked EST file is gzipped. Format will be checked later after extraction.\n"
				}
			}

			// upload of structure file
			def uploadedStructFile = request.getFile('StructFile')
			// check file size
			preUploadSize = uploadedStructFile.getSize()
			if(!uploadedStructFile.empty){
				if(preUploadSize <= maxButtonFileSize * 2){
					projectDir.mkdirs()
					uploadedStructFile.transferTo( new File (projectDir, "training-gene-structure.gff"))
					trainingInstance.struct_file = uploadedStructFile.originalFilename
					confirmationString = "${confirmationString}Training gene structure file: ${trainingInstance.struct_file}\n"
					logDate = new Date()
					logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - Uploaded training gene structure file ${uploadedStructFile.originalFilename} was renamed to training-gene-structure.gff and moved to ${projectDir}\n"
					def gffColErrorFlag = 0
					def gffNameErrorFlag = 0
					if(!uploadedGenomeFile.empty){ // if seqNames already exists
						// gff format validation: number of columns 9, + or - in column 7, column 1 muss member von seqNames sein
						def gffArray
						def isElement
						new File("${projectDir}/training-gene-structure.gff").eachLine{line -> 
							// check whether weird metacharacters are included
							if(line =~ /\*/ || line =~ /\?/){
								metacharacterFlag = 1
							}else{
								if(line =~ /^LOCUS/){
									structureGbkFlag = 1 
								}
								if(structureGbkFlag == 0){
									gffArray = line.split("\t")
									if(!(gffArray.size() == 9)){ gffColErrorFlag = 1 }
									isElement = 0
									seqNames.each{ seq ->
										if(seq =~ /${gffArray[0]}/){ isElement = 1 }
										if(isElement == 0){ gffNameErrorFlag = 1 }
									}
								}
							}
						}
						if(metacharacterFlag == 1){
							logDate = new Date()
							logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - The gene structure file contains metacharacters (e.g. * or ?). ${projectDir} is deleted.\n";
							cmdStr = "rm -r ${projectDir} &> /dev/null"
							delProc = "${cmdStr}".execute()
							if(verb > 1){
								logDate = new Date()
								logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
							}
							delProc.waitFor()
							logDate = new Date()
							logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - Job ${trainingInstance.accession_id} by user ${trainingInstance.email_adress} is aborted!\n"
							flash.error = "Gene Structure file contains metacharacters (*, ?, ...). This is not allowed."
							redirect(action:create, params:[email_adress:"${trainingInstance.email_adress}", project_name:"${trainingInstance.project_name}"])
							return
						}
						if(gffColErrorFlag == 1 && structureGbkFlag == 0){
							logDate = new Date()
							logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - Training gene structure file does not always contain 9 columns.\n"
							flash.error = "Training gene structure file  ${trainingInstance.struct_file} is not in a compatible gff format (has not 9 columns). Please make sure the gff-format complies with the instructions in our 'Help' section!"
						}
						if(gffNameErrorFlag == 1 && structureGbkFlag == 0){
							logDate = new Date()
							logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - Training gene structure file contains entries that do not comply with genome sequence names.\n"
							flash.error = "Entries in the training gene structure file  ${trainingInstance.struct_file} do not match the sequence names of the genome file. Please make sure the gff-format complies with the instructions in our 'Help' section!"
						}
						if((gffColErrorFlag == 1 || gffNameErrorFlag == 1) && structureGbkFlag == 0){
							logDate = new Date()
							logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - ${projectDir} is deleted.\n"
							cmdStr = "rm -r ${projectDir} &> /dev/null"
							delProc = "${cmdStr}".execute()
							if(verb > 1){
								logDate = new Date()
								logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
							}
							delProc.waitFor()
							logDate = new Date()
							logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - Job ${trainingInstance.accession_id} by user ${trainingInstance.email_adress} is aborted!\n"
							redirect(action:create, params:[email_adress:"${trainingInstance.email_adress}", project_name:"${trainingInstance.project_name}"])
							return
						}
					}

				}else{
					def allowedStructSize = maxButtonFileSize * 2
					logDate = new Date()
					logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - The selected training gene structure file was bigger than ${allowedStructSize}. Submission rejected.\n"
					flash.error = "Training gene structure file is bigger than ${allowedStructSize} bytes, which is our maximal size for file upload from local harddrives via web browser. Please select a smaller file or use the ftp/http web link file upload option."
					redirect(action:create, params:[email_adress:"${trainingInstance.email_adress}", project_name:"${trainingInstance.project_name}"])
					return	
				}
				structureExistsFlag = 1
				def structCksumScript = new File("${projectDir}/struct_cksum.sh")
				def structCksumFile = "${projectDir}/struct.cksum"
				cmd2Script = "cksum ${projectDir}/training-gene-structure.gff > ${structCksumFile} 2> /dev/null"
				structCksumScript << "${cmd2Script}"
				if(verb > 2){
					logDate = new Date()
					logFile <<  "${logDate} ${trainingInstance.accession_id} v3 - structCksumScript << \"${cmd2Script}\"\n"
				}
				cmdStr = "bash ${projectDir}/struct_cksum.sh"
				def structCksumProcess = "${cmdStr}".execute()
				if(verb > 1){
					logDate = new Date()
					logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
				}
				structCksumProcess.waitFor()
				def structCksumContent = new File("${structCksumFile}").text
				def structCksum_array = structCksumContent =~/(\d*) \d* /
				def structCksum
				(1..structCksum_array.groupCount()).each{structCksum = "${structCksum_array[0][it]}"}
				trainingInstance.struct_cksum = "${structCksum}"
				trainingInstance.struct_size = uploadedStructFile.size
				logDate = new Date()
				logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - struct.fa is ${trainingInstance.struct_size} big and has a cksum of ${structCksum}.\n"
				cmdStr = "rm ${projectDir}/struct.cksum &> /dev/null"
				delProc = "${cmdStr}".execute()
				if(verb > 1){
					logDate = new Date()
					logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
				}
				delProc.waitFor()
				cmdStr = "rm ${projectDir}/struct_cksum.sh &> /dev/null"
				delProc = "${cmdStr}".execute()
				if(verb > 1){
					logDate = new Date()
					logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
				}
				delProc.waitFor()
			}


			// upload of protein file
			def cRatio = 0
			def uploadedProteinFile = request.getFile('ProteinFile')
			// check file size
			preUploadSize = uploadedProteinFile.getSize()
			if(!uploadedProteinFile.empty){
				if(preUploadSize <= maxButtonFileSize){
					projectDir.mkdirs()
					uploadedProteinFile.transferTo( new File (projectDir, "protein.fa"))
					trainingInstance.protein_file = uploadedProteinFile.originalFilename
					confirmationString = "${confirmationString}Protein file: ${trainingInstance.protein_file}\n"
				}else{
					logDate = new Date()
					logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - The selected protein file was bigger than ${maxButtonFileSize}. Submission rejected.\n"
					flash.error = "Protein file is bigger than ${maxButtonFileSize} bytes, which is our maximal size for file upload from local harddrives via web browser. Please select a smaller file or use the ftp/http web link file upload option."
					redirect(action:create, params:[email_adress:"${trainingInstance.email_adress}", project_name:"${trainingInstance.project_name}"])
					return	
				}
				if("${uploadedProteinFile.originalFilename}" =~ /\.gz/){
					logDate = new Date()
					logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - Protein file is gzipped.\n"
					def gunzipProteinScript = new File("${projectDir}/gunzipProtein.sh")
					cmd2Script = "cd ${projectDir}; mv protein.fa protein.fa.gz &> /dev/null; gunzip protein.fa.gz 2> /dev/null"
					gunzipProteinScript << "${cmd2Script}"
					if(verb > 2){
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v3 - gunzipProteinScript << \"${cmd2Script}\"\n"
					}
					cmdStr = "bash ${gunzipProteinScript}"
					def gunzipProtein = "${cmdStr}".execute()
					if(verb > 1){
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
					}
					gunzipProtein.waitFor()
					logDate = new Date()
					logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - Unpacked Protein file.\n"
					cmdStr = "rm ${gunzipProteinScript} &> /dev/null"
					delProc = "${cmdStr}".execute()
					if(verb > 1){
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
					}
					delProc.waitFor()
				}
				logDate = new Date()
				logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - Uploaded protein file ${uploadedProteinFile.originalFilename} was renamed to protein.fa and moved to ${projectDir}\n"
				// check fasta format
					// check that file contains protein sequence, here defined as not more than 5 percent C or c
				def cytosinCounter = 0 // C is cysteine in amino acids, and cytosine in DNA.
				def allAminoAcidsCounter = 0
				new File("${projectDir}/protein.fa").eachLine{line -> 
					if(line =~ /\*/ || line =~ /\?/){
						metacharacterFlag = 1
					}else{
						if(!(line =~ /^[>AaRrNnDdCcEeQqGgHhIiLlKkMmFfPpSsTtWwYyVvBbZzJjXx ]/) && !(line =~ /^$/)){ proteinFastaFlag = 1 }
						if(!(line =~ /^>/)){
							line.eachMatch(/[AaRrNnDdCcEeQqGgHhIiLlKkMmFfPpSsTtWwYyVvBbZzJjXx]/){allAminoAcidsCounter = allAminoAcidsCounter + 1}
							line.eachMatch(/[Cc]/){cytosinCounter = cytosinCounter + 1}
						}
					}
				}
				if(metacharacterFlag == 1){
					logDate = new Date()
					logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - The protein file contains metacharacters (e.g. * or ?). ${projectDir} is deleted.\n";
					cmdStr = "rm -r ${projectDir} &> /dev/null"
					delProc = "${cmdStr}".execute()
					if(verb > 1){
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
					}
					delProc.waitFor()
					logDate = new Date()
					logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - Job ${trainingInstance.accession_id} by user ${trainingInstance.email_adress} is aborted!\n"
					flash.error = "The protein file contains metacharacters (*, ?, ...). This is not allowed."
					redirect(action:create, params:[email_adress:"${trainingInstance.email_adress}", project_name:"${trainingInstance.project_name}"])
					return
				}
				cRatio = cytosinCounter/allAminoAcidsCounter
				if (cRatio >= 0.05){
					logDate = new Date()
					logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - The protein file was with cysteine ratio ${cRatio} not recognized as protein file (probably DNA sequence). ${projectDir} is deleted.\n"
					cmdStr = "rm -r ${projectDir} &> /dev/null"
					delProc = "${cmdStr}".execute()
					if(verb > 1){
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
					}
					delProc.waitFor()
					logDate = new Date()
					logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - Job ${trainingInstance.accession_id} by user ${trainingInstance.email_adress} is aborted!\n"
					flash.error = "Your protein file was not recognized as a protein file. It may be DNA file. The training job was not started. Please contact augustus@uni-greifswald.de if you are completely sure this file is a protein fasta file."
					redirect(action:create, params:[email_adress:"${trainingInstance.email_adress}", project_name:"${trainingInstance.project_name}"])
					return
				}
				if(proteinFastaFlag == 1) {
					logDate = new Date()
					logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - The protein file was not protein fasta. ${projectDir} is deleted.\n"
					cmdStr = "rm -r ${projectDir} &> /dev/null"
					delProc = "${cmdStr}".execute()
					if(verb > 1){
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
					}
					delProc.waitFor()
					logDate = new Date()
					logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - Job ${trainingInstance.accession_id} by user ${trainingInstance.email_adress} is aborted!\n"
					flash.error = "Protein file ${uploadedProteinFile.originalFilename} is not in protein fasta format."
					redirect(action:create, params:[email_adress:"${trainingInstance.email_adress}", project_name:"${trainingInstance.project_name}"])
					return
				}
				proteinExistsFlag = 1
				def proteinCksumScript = new File("${projectDir}/protein_cksum.sh")
				def proteinCksumFile = "${projectDir}/protein.cksum"
				cmd2Script = "cksum ${projectDir}/protein.fa > ${proteinCksumFile} 2> /dev/null"
				proteinCksumScript << "${cmd2Script}"
				if(verb > 2){
					logDate = new Date()
					logFile <<  "${logDate} ${trainingInstance.accession_id} v3 - proteinCksumScript << \"${cmd2Script}\"\n"
				}
				cmdStr = "bash ${projectDir}/protein_cksum.sh"
				def proteinCksumProcess = "${cmdStr}".execute()
				if(verb > 1){
					logDate = new Date()
					logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
				}
				proteinCksumProcess.waitFor()
				def proteinCksumContent = new File("${proteinCksumFile}").text
				def proteinCksum_array = proteinCksumContent =~/(\d*) \d* /
				def proteinCksum
				(1..proteinCksum_array.groupCount()).each{proteinCksum = "${proteinCksum_array[0][it]}"}
				trainingInstance.protein_cksum = "${proteinCksum}"
				trainingInstance.protein_size = uploadedProteinFile.size
				logDate = new Date()
				logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - protein.fa is ${trainingInstance.protein_size} big and has a cksum of ${proteinCksum}.\n"
				cmdStr = "rm ${projectDir}/protein.cksum &> /dev/null"
				delProc = "${cmdStr}".execute()
				if(verb > 1){
					logDate = new Date()
					logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
				}
				delProc.waitFor()
				delProc = "rm ${projectDir}/protein_cksum.sh &> /dev/null".execute()
				delProc.waitFor()
			}

			// retrieve beginning of protein file for format check 
			if(!(trainingInstance.protein_ftp_link == null)){
				logDate = new Date()
				logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - protein web-link is ${trainingInstance.protein_ftp_link}\n"
				projectDir.mkdirs()
				confirmationString = "${confirmationString}Protein file: ${trainingInstance.protein_ftp_link}\n"
				if(!("${trainingInstance.protein_ftp_link}" =~ /\.gz/)){
					def URL url = new URL("${trainingInstance.protein_ftp_link}");
					// check whether URL exists
					urlExistsScript = new File("${projectDir}/proteinExists.sh")
					cmd2Script = "curl -o /dev/null --silent --head --write-out '%{http_code}\n' \"${trainingInstance.protein_ftp_link}\" > ${projectDir}/proteinExists"
					urlExistsScript << "${cmd2Script}"
					if(verb > 2){
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v3 - urlExistsScript << \"${cmd2Script}\"\n"
					}
					cmdStr = "bash ${urlExistsScript}"
					def genomeUrlExists = "${cmdStr}".execute()
					if(verb > 1){
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
					}
					genomeUrlExists.waitFor()
					cmdStr = "rm ${urlExistsScript} &> /dev/null"			
					delProc = "${cmdStr}".execute()
					if(verb > 1){
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
					}
					delProc.waitFor()
					content = new File("${projectDir}/proteinExists").text
					st = new Scanner(content)//works for exactly one number in a file
					error_code = st.nextInt();
					if(!(error_code == 200)){
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - The protein URL is not accessible. Response code: ${error_code}. Aborting job.\n"
						cmdStr = "rm -r ${projectDir} &> /dev/null"
						delProc = "${cmdStr}".execute()
						if(verb > 1){
							logDate = new Date()
							logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
						}
						delProc.waitFor()
						flash.error = "Cannot retrieve protein file from HTTP/FTP link ${trainingInstance.protein_ftp_link}."
						redirect(action:create, params:[email_adress:"${trainingInstance.email_adress}", project_name:"${trainingInstance.project_name}"])
						return
					}else{
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - The protein URL is accessible. Response code: ${error_code}.\n"
					}
					// checking web file for protein fasta format: 
					def URLConnection uc = url .openConnection()
					def BufferedReader br = new BufferedReader(new InputStreamReader(uc.getInputStream()))
					try{
						def String inputLine=null
						def lineCounter = 1;
						def cytosinCounter = 0 // C is cysteine in amino acids, and cytosine in DNA.
						def allAminoAcidsCounter = 0
						while ( ((inputLine = br.readLine()) != null) && (lineCounter <= 50)) {
							if(!(inputLine =~ /^[>AaRrNnDdCcEeQqGgHhIiLlKkMmFfPpSsTtWwYyVvBbZzJjXx]/) && !(inputLine =~ /^$/)){ proteinFastaFlag = 1 }
							if(!(inputLine =~ /^>/)){
								inputLine.eachMatch(/[AaRrNnDdCcEeQqGgHhIiLlKkMmFfPpSsTtWwYyVvBbZzJjXx]/){allAminoAcidsCounter = allAminoAcidsCounter + 1}
								inputLine.eachMatch(/[Cc]/){cytosinCounter = cytosinCounter + 1}
							}
						}
						cRatio = cytosinCounter/allAminoAcidsCounter
					}finally{
						br.close()
					}
					if (cRatio >= 0.05){
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v1 -  The protein file was with cysteine ratio ${cRatio} not recognized as protein file (probably DNA sequence).\n ${projectDir} is deleted.\n"
						cmdStr = "rm -r ${projectDir} &> /dev/null"
						delProc = "${cmdStr}".execute()
						if(verb > 1){
							logDate = new Date()
							logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
						}
						delProc.waitFor()
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - Job ${trainingInstance.accession_id} by user ${trainingInstance.email_adress} is aborted!\n"
						flash.error = "Protein file ${trainingInstance.protein_ftp_link} does not contain protein sequences."
						redirect(action:create)
						return
					}
					if(proteinFastaFlag == 1) {
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v1 -  The protein file was not protein fasta. ${projectDir} is deleted.\n"
						cmdStr = "rm -r ${projectDir} &> /dev/null"
						delProc = "${cmdStr}".execute()
						if(verb > 1){
							logDate = new Date()
							logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
						}
						delProc.waitFor()
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v1 -  Job ${trainingInstance.accession_id} by user ${trainingInstance.email_adress} is aborted!\n"
						flash.message = "Protein file ${trainingInstance.protein_ftp_link} is not in protein fasta format."
						redirect(action:create, params:[email_adress:"${trainingInstance.email_adress}", project_name:"${trainingInstance.project_name}"])
						return
					}
				}else{
					logDate = new Date()
					logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - The linked Protein file is gzipped. Format will be checked later after extraction.\n"
				}
						
			}
			// send confirmation email and redirect
			if(!trainingInstance.hasErrors() && trainingInstance.save()){
				// generate empty results page
				def emptyPageScript = new File("${projectDir}/emptyPage.sh")
				cmd2Script = "${AUGUSTUS_SCRIPTS_PATH}/writeResultsPage.pl ${trainingInstance.accession_id} ${trainingInstance.project_name} ${dbFile} ${output_dir} ${web_output_dir} ${AUGUSTUS_CONFIG_PATH} ${AUGUSTUS_SCRIPTS_PATH} 0 &> /dev/null"
				emptyPageScript << "${cmd2Script}"
				if(verb > 2){
					logDate = new Date()
					logFile <<  "${logDate} ${trainingInstance.accession_id} v3 - emptyPageScript << \"${cmd2Script}\"\n"
				}
				cmdStr = "bash ${projectDir}/emptyPage.sh"
				def emptyPageExecution = "${cmdStr}".execute()
				if(verb > 1){
					logDate = new Date()
					logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
				}
				emptyPageExecution.waitFor()
				trainingInstance.job_status = 0
				emailStr = "Hello!\n\nThank you for submitting a job to train AUGUSTUS parameters for species ${trainingInstance.project_name}. The job status is available at http://bioinf.uni-greifswald.de/augustus-training-0.1/training/show/${trainingInstance.id}.\n\nDetails of your job:\n\n${confirmationString}\nYou will be notified by e-mail after computations of your job have finished. You will then find the results of your job at http://bioinf.uni-greifswald.de/trainaugustus/training-results/${trainingInstance.accession_id}/index.html.\n\nBest regards,\n\nthe AUGUSTUS web server team\n\nhttp://bioinf.uni-greifswald.de/trainaugustus\n"
				sendMail {
					to "${trainingInstance.email_adress}"
					subject "Your AUGUSTUS training job ${trainingInstance.accession_id}"
					body """${emailStr}"""
				}
				logDate = new Date()
				logFile <<  "${logDate} ${trainingInstance.accession_id} v1 -  Confirmation e-mail sent.\n"          
				redirect(action:show,id:trainingInstance.id)
				//forward(action:"show",id:trainingInstance.id)
				//forward action: "show", id: trainingInstance.id
			} else {
				logDate = new Date()
				logFile <<  "${logDate} ${trainingInstance.accession_id} v1 -  An error occurred in the trainingInstance (e.g. E-Mail missing, see domain restrictions).\n"
				logDate = new Date()
				logFile <<  "${logDate} ${trainingInstance.accession_id} v1 -  ${projectDir} is deleted.\n"
				cmdStr = "rm -r ${projectDir} &> /dev/null"
				delProc = "${cmdStr}".execute()
				if(verb > 1){
					logDate = new Date()
					logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
				}
				delProc.waitFor()
				logDate = new Date()
				logFile <<  "${logDate} ${trainingInstance.accession_id} v1 -  Job ${trainingInstance.accession_id} by user ${trainingInstance.email_adress} is aborted!\n"
				render(view:'create', model:[trainingInstance:trainingInstance])
				return
			}

			//---------------------  BACKGROUND PROCESS ----------------------------
			Thread.start{
				// retrieve genome file
				if(!(trainingInstance.genome_ftp_link == null)){
					logDate = new Date()
					logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - Checking genome file size with curl prior upload\n"
					projectDir.mkdirs()
					// check whether the genome file is small enough for upload
					def fileSizeScript = new File("${projectDir}/filzeSize.sh")
					cmd2Script = "curl -sI ${trainingInstance.genome_ftp_link} | grep Content-Length | cut -d ' ' -f 2 > ${projectDir}/genomeFileSize 2> /dev/null"
					fileSizeScript << "${cmd2Script}"
					if(verb > 2){
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v3 - fileSizeScript << \"${cmd2Script}\"\n"
					}
					cmdStr = "bash ${fileSizeScript}"
					def retrieveFileSize = "${cmdStr}".execute()
					if(verb > 1){
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
					}
					retrieveFileSize.waitFor()
					cmdStr = "rm ${fileSizeScript} &> /dev/null"			
					def delSzCrProc = "${cmdStr}".execute()
					if(verb > 1){
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
					}
					delSzCrProc.waitFor()
					content = new File("${projectDir}/genomeFileSize").text
					st = new Scanner(content)//works for exactly one number in a file
					def int genome_size;
					genome_size = st.nextInt();
					if(genome_size < maxFileSizeByWget){//1 GB
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - Retrieving genome file ${trainingInstance.genome_ftp_link}\n"
						def getGenomeScript = new File("${projectDir}/getGenome.sh")
						cmd2Script = "wget -O ${projectDir}/genome.fa ${trainingInstance.genome_ftp_link} > ${projectDir}/getGenome.out 2> /dev/null"
						getGenomeScript << "${cmd2Script}"
						if(verb > 2){
							logDate = new Date()
							logFile <<  "${logDate} ${trainingInstance.accession_id} v3 - getGenomeScript << \"${cmd2Script}\"\n"
						}
						cmdStr = "bash ${projectDir}/getGenome.sh"
						def wgetGenome = "${cmdStr}".execute()
						if(verb > 1){
							logDate = new Date()
							logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
						}
						wgetGenome.waitFor()
						delProc = "rm ${projectDir}/getGenome.sh &> /dev/null".execute()
						delProc.waitFor()
						if("${trainingInstance.genome_ftp_link}" =~ /\.gz/){
							def gunzipGenomeScript = new File("${projectDir}/gunzipGenome.sh")
							cmd2Script = "cd ${projectDir}; mv genome.fa genome.fa.gz &> /dev/null; gunzip genome.fa.gz 2> /dev/null"
							gunzipGenomeScript << "${cmd2Script}"
							if(verb > 2){
								logDate = new Date()
								logFile <<  "${logDate} ${trainingInstance.accession_id} v3 - gunzipGenomeScript << \"${cmd2Script}\"\n"
							}
							cmdStr = "bash ${gunzipGenomeScript}"
							def gunzipGenome = "${cmdStr}".execute()
							if(verb > 1){
								logDate = new Date()
								logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
							}
							gunzipGenome.waitFor()	
							cmdStr = "rm ${gunzipGenomeScript} &> /dev/null"		
							delProc = "${cmdStr}".execute()
							if(verb > 1){
								logDate = new Date()
								logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
							}
							delProc.waitFor()
							logDate = new Date()
							logFile <<  "${logDate} ${trainingInstance.accession_id} v1 -Unpacked genome file.\n"
						}
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - genome file upload finished, file stored as genome.fa at ${projectDir}\n"
						// check for fasta format & get seq names for gff validation:
						new File("${projectDir}/genome.fa").eachLine{line -> 
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
							logDate = new Date()
							logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - The genome file contains metacharacters (e.g. * or ?). ${projectDir} is deleted.\n";
							cmdStr = "rm -r ${projectDir} &> /dev/null"
							delProc = "${cmdStr}".execute()
							if(verb > 1){
								logDate = new Date()
								logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
							}
							delProc.waitFor()
							logDate = new Date()
							logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - Job ${trainingInstance.accession_id} by user ${trainingInstance.email_adress} is aborted!\n"
							sendMail {
								to "${trainingInstance.email_adress}"
								subject "Your AUGUSTUS training job ${trainingInstance.accession_id} was aborted"
								body """Hello!

Your AUGUSTUS training job ${trainingInstance.accession_id} for species ${trainingInstance.project_name} was aborted because the provided genome file ${trainingInstance.genome_ftp_link} contains metacharacters (e.g. * or ?). This is not allowed.

Best regards,

the AUGUSTUS web server team

http://bioinf.uni-greifswald.de/trainaugustus
"""	
							}
							// delete database entry
							trainingInstance.delete()
							return
						}
						if(genomeFastaFlag == 1) {
							logDate = new Date()
							logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - The genome file was not fasta. ${projectDir} is deleted.\n"
							cmdStr = "rm -r ${projectDir} &> /dev/null"
							delProc = "${cmdStr}".execute()
							if(verb > 1){
								logDate = new Date()
								logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
							}
							delProc.waitFor()
							logDate = new Date()
							logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - Job ${trainingInstance.accession_id} by user ${trainingInstance.email_adress} is aborted!\n"
							sendMail {
								to "${trainingInstance.email_adress}"
								subject "Your AUGUSTUS training job ${trainingInstance.accession_id} was aborted"
								body """Hello!

Your AUGUSTUS training job ${trainingInstance.accession_id} for species ${trainingInstance.project_name} was aborted because the provided genome file ${trainingInstance.genome_ftp_link} was not in DNA fasta format.

Best regards,

the AUGUSTUS web server team

http://bioinf.uni-greifswald.de/trainaugustus
"""	
							}
							// delete database entry
							trainingInstance.delete()
							return
						}
					}else{// actions if remote file was bigger than allowed
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v1 -  Genome file size exceeds permitted ${maxFileSizeByWget} bytes. Abort job.\n"
						def errorStrMsg = "Hello!\nYour AUGUSTUS training job ${trainingInstance.accession_id} for species ${trainingInstance.project_name} was aborted because the genome file size was with ${genome_size} bytes bigger than ${maxFileSizeByWget} bytes. Please submitt a smaller genome file!\n\nBest regards,\n\nthe AUGUSTUS web server team\n\nhttp://bioinf.uni-greifswald.de/trainaugustus\n"
						sendMail {
								to "${trainingInstance.email_adress}"
								subject "Your AUGUSTUS training job ${trainingInstance.accession_id} was aborted"
								body """${errorStrMsg}"""
						}
						cmdStr = "rm -r ${projectDir} &> /dev/null"
						delProc = "${cmdStr}".execute()
						if(verb > 1){
							logDate = new Date()
							logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
						}
						delProc.waitFor()
						// delete database entry
						trainingInstance.delete()
						return
					}

					// check gff format
					def gffColErrorFlag = 0
					def gffNameErrorFlag = 0
					if((!uploadedStructFile.empty) &&(!(trainingInstance.genome_ftp_link == null))){ // if seqNames already exists
						// gff format validation: number of columns 9, + or - in column 7, column 1 muss member von seqNames sein
						def gffArray
						def isElement
						new File("${projectDir}/training-gene-structure.gff").eachLine{line -> 

							if(line =~ /\*/ || line =~ /\?/){
								metacharacterFlag = 1
							}else{

								if(line =~ /^LOCUS/){
									structureGbkFlag = 1 
								}
								if(structureGbkFlag == 0){
									gffArray = line.split("\t")
									if(!(gffArray.size() == 9)){ gffColErrorFlag = 1 }
									isElement = 0
									seqNames.each{ seq ->
										if(seq =~ /${gffArray[0]}/){ isElement = 1 }
										if(isElement == 0){ gffNameErrorFlag = 1 }
									}
								}
							}
						}
						if(metacharacterFlag == 1){
							logDate = new Date()
							logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - The gene structure file contains metacharacters (e.g. * or ?). ${projectDir} is deleted.\n";
							cmdStr = "rm -r ${projectDir} &> /dev/null"
							delProc = "${cmdStr}".execute()
							if(verb > 1){
								logDate = new Date()
								logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
							}
							delProc.waitFor()
							logDate = new Date()
							logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - Job ${trainingInstance.accession_id} by user ${trainingInstance.email_adress} is aborted!\n"
							sendMail {
								to "${trainingInstance.email_adress}"
								subject "Your AUGUSTUS training job ${trainingInstance.accession_id} was aborted"
								body """Hello!

Your AUGUSTUS training job ${trainingInstance.accession_id} for species ${trainingInstance.project_name} was aborted because the provided gene structure file contains metacharacters (e.g. * or ?). This is not allowed.

Best regards,

the AUGUSTUS web server team

http://bioinf.uni-greifswald.de/trainaugustus
"""	
							}
							// delete database entry
							trainingInstance.delete()
							return
						}
						if(gffColErrorFlag == 1 && structureGbkFlag == 0){
							logDate = new Date()
							logFile <<  "${logDate} ${trainingInstance.accession_id} v1 -  Training gene structure file does not always contain 9 columns.\n"
							sendMail {
								to "${trainingInstance.email_adress}"
								subject "Your AUGUSTUS training job ${trainingInstance.accession_id} was aborted"
								body """Hello!

Your AUGUSTUS training job ${trainingInstance.accession_id} for species ${trainingInstance.project_name} was aborted because the provided training gene structure file ${trainingInstance.struct_file} did not contain 9 columns in each line. Please make sure the gff-format complies with the instructions in our 'Help' section before submitting another job!

Best regards,

the AUGUSTUS web server team

http://bioinf.uni-greifswald.de/trainaugustus/
"""
							}
						}
						if(gffNameErrorFlag == 1 && structureGbkFlag == 0){
							logDate = new Date()
							logFile <<  "${logDate} ${trainingInstance.accession_id} v1 -  Training gene structure file contains entries that do not comply with genome sequence names.\n"
							sendMail {
								to "${trainingInstance.email_adress}"
								subject "Your AUGUSTUS training job ${trainingInstance.accession_id} was aborted"
								body """Hello!

Your AUGUSTUS training job ${trainingInstance.accession_id} for species ${trainingInstance.project_name} was aborted because the sequence names in the provided training gene structure file ${trainingInstance.struct_file} did not comply with the sequence names in the supplied genome file ${trainingInstance.genome_ftp_link}. Please make sure the gff-format complies with the instructions in our 'Help' section before submitting another job!

Best regards,

the AUGUSTUS web server team

http://bioinf.uni-greifswald.de/trainaugustus
"""
							}
						}
						if((gffColErrorFlag == 1 || gffNameErrorFlag == 1) && structureGbkFlag == 0){
							logDate = new Date()
							logFile <<  "${logDate} ${trainingInstance.accession_id} v1 -  ${projectDir} is deleted.\n"
							cmdStr = "rm -r ${projectDir} &> /dev/null"
							delProc = "${cmdStr}".execute()
							if(verb > 1){
								logDate = new Date()
								logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
							}
							delProc.waitFor()
							logDate = new Date()
							logFile <<  "${logDate} ${trainingInstance.accession_id} v1 -  Job ${trainingInstance.accession_id} by user ${trainingInstance.email_adress} is aborted!\n"
							// delete database entry
							trainingInstance.delete()
							return
						}
					}
					def genomeCksumScript = new File("${projectDir}/genome_cksum.sh")
					def genomeCksumFile = "${projectDir}/genome.cksum"
					cmd2Script = "cksum ${projectDir}/genome.fa > ${genomeCksumFile} 2> /dev/null"
					genomeCksumScript << "${cmd2Script}"
					if(verb > 2){
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v3 - genomeCksumScript << \"${cmd2Script}\"\n"
					}
					cmdStr = "bash ${projectDir}/genome_cksum.sh"
					def genomeCksumProcess = "${cmdStr}".execute()
					if(verb > 1){
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
					}
					genomeCksumProcess.waitFor()
					def genomeCksumContent = new File("${genomeCksumFile}").text
					def genomeCksum_array = genomeCksumContent =~/(\d*) \d* /
					def genomeCksum
					(1..genomeCksum_array.groupCount()).each{genomeCksum = "${genomeCksum_array[0][it]}"}
					trainingInstance.genome_cksum = "${genomeCksum}"
					genomeCksum_array = genomeCksumContent =~/\d* (\d*) /
					trainingInstance.genome_size
					(1..genomeCksum_array.groupCount()).each{trainingInstance.genome_size = "${genomeCksum_array[0][it]}"}
					logDate = new Date()
					logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - genome.fa is ${trainingInstance.genome_size} big and has a cksum of ${genomeCksum}.\n"
					cmdStr = "rm ${projectDir}/genome.cksum &> /dev/null"
					delProc = "${cmdStr}".execute()
					if(verb > 1){
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
					}
					delProc.waitFor()
					cmdStr = "rm ${projectDir}/genome_cksum.sh &> /dev/null"
					delProc = "${cmdStr}".execute()
					if(verb > 1){
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
					}
					delProc.waitFor()
				} // end of if(!(trainingInstance.genome_ftp_link == null))

				// retrieve EST file
				if(!(trainingInstance.est_ftp_link == null)){
					// check whether the EST file is small enough for upload
					logDate = new Date()
					logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - Checking cDNA file size with curl prior upload\n"
					def fileSizeScript = new File("${projectDir}/filzeSize.sh")
					cmd2Script = "curl -sI ${trainingInstance.est_ftp_link} | grep Content-Length | cut -d ' ' -f 2 > ${projectDir}/estFileSize 2> /dev/null"
					fileSizeScript << "${cmd2Script}"
					if(verb > 2){
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v3 - fileSizeScript << \"${cmd2Script}\"\n"
					}
					cmdStr = "bash ${fileSizeScript}"
					def retrieveFileSize = "${cmdStr}".execute()
					if(verb > 1){
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
					}
					retrieveFileSize.waitFor()
					cmdStr = "rm ${fileSizeScript} &> /dev/null"		
					def delSzCrProc = "${cmdStr}".execute()
					if(verb > 1){
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
					}
					delSzCrProc.waitFor()
					content = new File("${projectDir}/estFileSize").text
					st = new Scanner(content)//works for exactly one number in a file
					def int est_size;
					est_size = st.nextInt();
					if(est_size < maxFileSizeByWget){//1 GB
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - Retrieving EST/cDNA file ${trainingInstance.est_ftp_link}\n"
						def getEstScript = new File("${projectDir}/getEstScript.sh")
						cmd2Script = "wget -O ${projectDir}/est.fa ${trainingInstance.est_ftp_link} 2> /dev/null"
						getEstScript << "${cmd2Script}"
						if(verb > 2){
							logDate = new Date()
							logFile <<  "${logDate} ${trainingInstance.accession_id} v3 - getEstScript << \"${cmd2Script}\"\n"
						}
						cmdStr = "bash ${projectDir}/getEstScript.sh"
						def wgetEst = "${cmdStr}".execute()
						if(verb > 1){
							logDate = new Date()
							logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
						}
						wgetEst.waitFor()
						if("${trainingInstance.est_ftp_link}" =~ /\.gz/){
							def gunzipEstScript = new File("${projectDir}/gunzipEst.sh")
							cmd2Script = "cd ${projectDir}; mv est.fa est.fa.gz &> /dev/null; gunzip est.fa.gz 2> /dev/null"
							gunzipEstScript << "${cmd2Script}"
							if(verb > 2){
								logDate = new Date()
								logFile <<  "${logDate} ${trainingInstance.accession_id} v3 - gunzipEstScript << \"${cmd2Script}\"\n"
							}
							cmdStr = "bash ${gunzipEstScript}"
							def gunzipEst = "${cmdStr}".execute()
							if(verb > 1){
								logDate = new Date()
								logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
							}
							gunzipEst.waitFor()	
							cmdStr = "rm ${gunzipEstScript} &> /dev/null"		
							delProc = "${cmdStr}".execute()
							if(verb > 1){
								logDate = new Date()
								logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
							}
							delProc.waitFor()
							logDate = new Date()
							logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - Unpacked EST file.\n"
						}					
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - EST/cDNA file upload finished, file stored as est.fa at ${projectDir}\n"
						// check for fasta format:
						new File("${projectDir}/est.fa").eachLine{line -> 
							if(line =~ /\*/ || line =~ /\?/){
								metacharacterFlag = 1
							}else{
								if(!(line =~ /^[>AaTtGgCcHhXxRrYyWwSsMmKkBbVvDdNn]/) && !(line =~ /^$/)){
									 estFastaFlag = 1 
								}
							}
						}
						if(metacharacterFlag == 1){
							logDate = new Date()
							logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - The cDNA file contains metacharacters (e.g. * or ?). ${projectDir} is deleted.\n";
							cmdStr = "rm -r ${projectDir} &> /dev/null"
							delProc = "${cmdStr}".execute()
							if(verb > 1){
								logDate = new Date()
								logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
							}
							delProc.waitFor()
							logDate = new Date()
							logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - Job ${trainingInstance.accession_id} by user ${trainingInstance.email_adress} is aborted!\n"
							sendMail {
								to "${trainingInstance.email_adress}"
								subject "Your AUGUSTUS training job ${trainingInstance.accession_id} was aborted"
								body """Hello!

Your AUGUSTUS training job ${trainingInstance.accession_id} for species ${trainingInstance.project_name} was aborted because the provided cDNA file ${trainingInstance.est_ftp_link} contains metacharacters (e.g. * or ?). This is not allowed.

Best regards,

the AUGUSTUS web server team

http://bioinf.uni-greifswald.de/trainaugustus
"""	
							}
							// delete database entry
							trainingInstance.delete()
							return
						}
						if(estFastaFlag == 1) {
							logDate = new Date()
							logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - The EST/cDNA file was not fasta. ${projectDir} is deleted.\n"
							cmdStr = "rm -r ${projectDir} &> /dev/null"
							delProc = "${cmdStr}".execute()
							if(verb > 1){
								logDate = new Date()
								logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
							}
							delProc.waitFor()
							logDate = new Date()
							logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - Job ${trainingInstance.accession_id} by user ${trainingInstance.email_adress} is aborted!\n"
							sendMail {
								to "${trainingInstance.email_adress}"
								subject "Your AUGUSTUS training job ${trainingInstance.accession_id} was aborted"
								body """Hello!

Your AUGUSTUS training job ${trainingInstance.accession_id} for species ${trainingInstance.project_name} was aborted because the provided cDNA file ${trainingInstance.est_ftp_link} was not in DNA fasta format.

Best regards,

the AUGUSTUS web server team

http://bioinf.uni-greifswald.de/trainaugustus
"""
							}
							// delete database entry
							trainingInstance.delete()
							return
						}
					}else{// actions if remote file was bigger than allowed
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v1 -  EST file size exceeds permitted ${maxFileSizeByWget} bytes. Abort job.\n"
						def errorStrMsg = "Hello!\nYour AUGUSTUS training job ${trainingInstance.accession_id} for species ${trainingInstance.project_name} was aborted because the cDNA file size was with ${est_size} bigger than 1 GB. Please submitt a smaller cDNA size!\n\nBest regards,\n\nthe AUGUSTUS web server team\n\nhttp://bioinf.uni-greifswald.de/trainaugustus\n"
						sendMail {
								to "${trainingInstance.email_adress}"
								subject "Your AUGUSTUS training job ${trainingInstance.accession_id} was aborted"
								body """${errorStrMsg}"""
						}
						cmdStr = "rm -r ${projectDir} &> /dev/null"
						delProc = "${cmdStr}".execute()
						if(verb > 1){
							logDate = new Date()
							logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
						}
						delProc.waitFor()
						// delete database entry
						trainingInstance.delete()
						return

					}
					def estCksumScript = new File("${projectDir}/est_cksum.sh")
					def estCksumFile = "${projectDir}/est.cksum"
					cmd2Script = "cksum ${projectDir}/est.fa > ${estCksumFile} 2> /dev/null"
					estCksumScript << "${cmd2Script}"
					if(verb > 2){
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v3 - \"${cmd2Script}\"\n"
					}
					cmdStr = "bash ${projectDir}/est_cksum.sh"
					def estCksumProcess = "${cmdStr}".execute()
					if(verb > 1){
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
					}
					estCksumProcess.waitFor()
					def estCksumContent = new File("${estCksumFile}").text
					def estCksum_array = estCksumContent =~/(\d*) \d* /
					def estCksum
					(1..estCksum_array.groupCount()).each{estCksum = "${estCksum_array[0][it]}"}
					trainingInstance.est_cksum = "${estCksum}"
					estCksum_array = estCksumContent =~/\d* (\d*) /
					trainingInstance.est_size
					(1..estCksum_array.groupCount()).each{trainingInstance.est_size = "${estCksum_array[0][it]}"}
					logDate = new Date()
					logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - est.fa is ${trainingInstance.est_size} big and has a cksum of ${estCksum}.\n"
					cmdStr = "rm ${projectDir}/est.cksum &> /dev/null"
					delProc = "${cmdStr}".execute()
					if(verb > 1){
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
					}
					delProc.waitFor()
					cmdStr = "rm ${projectDir}/est_cksum.sh &> /dev/null"
					delProc = "${cmdStr}".execute()
					if(verb > 1){
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
					}
					delProc.waitFor()
					estExistsFlag = 1
				} // end of if(!(trainingInstance.est_ftp_link == null))

				// check whether EST file is NOT RNAseq, i.e. does not contain on average very short entries
				def int nEntries = 0
				def int totalLen = 0
				if(estExistsFlag == 1){
					new File("${projectDir}/est.fa").eachLine{line -> 
						if(line =~ /^>/){
							nEntries = nEntries + 1
						}else{
							totalLen = totalLen + line.size()
						}
					}
					def avEstLen = totalLen/nEntries
					if(avEstLen < estMinLen){
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v1 -  EST sequences are on average shorter than ${estMinLen}, suspect RNAseq raw data. Abort job.\n"
						def errorStrMsg = "Hello!\nYour AUGUSTUS training job ${trainingInstance.accession_id} was aborted because the sequences in your cDNA file have an average length of ${avEstLen}. We suspect that sequences files with an average sequence length shorter than ${estMinLen} might contain RNAseq raw sequences. Currently, our web server application does not support the integration of RNAseq raw sequences. Please either assemble your sequences into longer contigs, or remove short sequences from your current file, or submitt a new job without specifying a cDNA file.\n\nBest regards,\n\nthe AUGUSTUS web server team\n\nhttp://bioinf.uni-greifswald.de/trainaugustus\n"
						sendMail {
								to "${trainingInstance.email_adress}"
								subject "Your AUGUSTUS training job ${trainingInstance.accession_id} was aborted"
								body """${errorStrMsg}"""
						}
						cmdStr = "rm -r ${projectDir} &> /dev/null"
						delProc = "${cmdStr}".execute()
						if(verb > 1){
							logDate = new Date()
							logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
						}
						delProc.waitFor()
						// delete database entry
						trainingInstance.delete()
						return
					}else if(avEstLen > estMaxLen){
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v1 -  EST sequences are on average longer than ${estMaxLen}, suspect non EST/cDNA data. Abort job.\n"
						def errorStrMsg = "Hello!\nYour AUGUSTUS training job ${trainingInstance.accession_id} was aborted because the sequences in your cDNA file have an average length of ${avEstLen}. We suspect that sequences files with an average sequence length longer than ${estMaxLen} might not contain ESTs or cDNAs. Please either remove long sequences from your current file, or submitt a new job without specifying a cDNA file.\n\nBest regards,\n\nthe AUGUSTUS web server team\n\nhttp://bioinf.uni-greifswald.de/trainaugustus\n"
						sendMail {
								to "${trainingInstance.email_adress}"
								subject "Your AUGUSTUS training job ${trainingInstance.accession_id} was aborted"
								body """${errorStrMsg}"""
						}
						cmdStr = "rm -r ${projectDir} &> /dev/null"
						delProc = "${cmdStr}".execute()
						if(verb > 1){
							logDate = new Date()
							logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
						}
						delProc.waitFor()
						// delete database entry
						trainingInstance.delete()
						return
					}
				}

				// retrieve protein file
				if(!(trainingInstance.protein_ftp_link == null)){
					// check whether the Protein file is small enough for upload
					logDate = new Date()
					logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - Checking protein file size with curl prior upload\n"
					def fileSizeScript = new File("${projectDir}/filzeSize.sh")
					cmd2Script = "curl -sI ${trainingInstance.protein_ftp_link} | grep Content-Length | cut -d ' ' -f 2 > ${projectDir}/proteinFileSize 2> /dev/null"
					fileSizeScript << "${cmd2Script}"
					if(verb > 2){
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v3 - fileSizeScript << \"${cmd2Script}\"\n"
					}
					cmdStr = "bash ${fileSizeScript}"
					def retrieveFileSize = "${cmdStr}".execute()
					if(verb > 1){
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
					}
					retrieveFileSize.waitFor()
					cmdStr = "rm ${fileSizeScript} &> /dev/null"		
					def delSzCrProc = "${cmdStr}".execute()
					if(verb > 1){
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
					}
					delSzCrProc.waitFor()
					content = new File("${projectDir}/proteinFileSize").text
					st = new Scanner(content)//works for exactly one number in a file
					def int protein_size;
					protein_size = st.nextInt();
					if(protein_size < maxFileSizeByWget){//1 GB
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - Retrieving protein file ${trainingInstance.protein_ftp_link}\n"
						def getProteinScript = new File("${projectDir}/getProtein.sh")
						cmd2Script = "wget -O ${projectDir}/protein.fa ${trainingInstance.protein_ftp_link} 2> /dev/null"
						getProteinScript << "${cmd2Script}"
						if(verb > 2){
							logDate = new Date()
							logFile <<  "${logDate} ${trainingInstance.accession_id} v3 - getProteinScript << \"${cmd2Script}\"\n"
						}
						cmdStr = "bash ${projectDir}/getProtein.sh"
						def wgetProtein = "${cmdStr}".execute()
						if(verb > 1){
							logDate = new Date()
							logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
						}
						wgetProtein.waitFor()
						if("${trainingInstance.protein_ftp_link}" =~ /\.gz/){
							def gunzipProteinScript = new File("${projectDir}/gunzipProtein.sh")
							cmd2Script = "cd ${projectDir}; mv protein.fa protein.fa.gz &> /dev/null; gunzip protein.fa.gz 2> /dev/null"
							gunzipProteinScript << "${cmd2Script}"	
							if(verb > 2){
								logDate = new Date()
								logFile <<  "${logDate} ${trainingInstance.accession_id} v3 - gunzipProteinScript << \"${cmd2Script}\"\n"
							}
							cmdStr = "bash ${gunzipProteinScript}"
							def gunzipProtein = "${cmdStr}".execute()
							if(verb > 1){
								logDate = new Date()
								logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
							}
							gunzipProtein.waitFor()		
							cmdStr = "rm ${gunzipProteinScript} &> /dev/null"	
							delProc = "${cmdStr}".execute()
							if(verb > 1){
								logDate = new Date()
								logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
							}
							delProc.waitFor()
							logDate = new Date()
							logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - Unpacked protein file.\n"
						}
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - Protein file upload finished, file stored as protein.fa at ${projectDir}\n"
						// check for fasta protein format:
						def cytosinCounter = 0 // C is cysteine in amino acids, and cytosine in DNA.
						def allAminoAcidsCounter = 0
						new File("${projectDir}/protein.fa").eachLine{line -> 
							if(line =~ /\*/ || line =~ /\?/){
								metacharacterFlag = 1
							}else{
								if(!(line =~ /^[>AaRrNnDdCcEeQqGgHhIiLlKkMmFfPpSsTtWwYyVvBbZzJjXx ]/) && !(line =~ /^$/)){ proteinFastaFlag = 1 }
								if(!(line =~ /^>/)){
									line.eachMatch(/[AaRrNnDdCcEeQqGgHhIiLlKkMmFfPpSsTtWwYyVvBbZzJjXx]/){ allAminoAcidsCounter = allAminoAcidsCounter + 1 }
									line.eachMatch(/[Cc]/){ cytosinCounter = cytosinCounter + 1 }
								}
							}
						}
						if(metacharacterFlag == 1){
							logDate = new Date()
							logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - The protein file contains metacharacters (e.g. * or ?). ${projectDir} is deleted.\n";
							cmdStr = "rm -r ${projectDir} &> /dev/null"
							delProc = "${cmdStr}".execute()
							if(verb > 1){
								logDate = new Date()
								logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
							}
							delProc.waitFor()
							logDate = new Date()
							logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - Job ${trainingInstance.accession_id} by user ${trainingInstance.email_adress} is aborted!\n"
							sendMail {
								to "${trainingInstance.email_adress}"
								subject "Your AUGUSTUS training job ${trainingInstance.accession_id} was aborted"
								body """Hello!

Your AUGUSTUS training job ${trainingInstance.accession_id} for species ${trainingInstance.project_name} was aborted because the provided protein file ${trainingInstance.protein_ftp_link} contains metacharacters (e.g. * or ?). This is not allowed.

Best regards,

the AUGUSTUS web server team

http://bioinf.uni-greifswald.de/trainaugustus
"""	
							}
							// delete database entry
							trainingInstance.delete()
							return
						}
						cRatio = cytosinCounter/allAminoAcidsCounter
						if (cRatio >= 0.05){
							logDate = new Date()
							logFile <<  "${logDate} ${trainingInstance.accession_id} v1 -  The protein file was with cysteine ratio ${cRatio} not recognized as protein file (probably DNA sequence). ${projectDir} is deleted.\n"
							cmdStr = "rm -r ${projectDir} &> /dev/null"
							delProc = "${cmdStr}".execute()
							if(verb > 1){
								logDate = new Date()
								logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
							}
							delProc.waitFor()
							logDate = new Date()
							logFile <<  "${logDate} ${trainingInstance.accession_id} v1 -  Job ${trainingInstance.accession_id} by user ${trainingInstance.email_adress} is aborted!\n"
							sendMail {
								to "${trainingInstance.email_adress}"
								subject "Your AUGUSTUS training job ${trainingInstance.accession_id} was aborted"
								body """Hello!

Your AUGUSTUS training job ${trainingInstance.accession_id} for species ${trainingInstance.project_name} was aborted because the provided protein file ${trainingInstance.protein_ftp_link} is suspected to contain DNA instead of protein sequences.

Best regards,

the AUGUSTUS web server team

http://bioinf.uni-greifswald.de/trainaugustus
"""		
							}
							// delete database entry
							trainingInstance.delete()
							return
						}
					}else{// actions if remote file was bigger than allowed
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v1 -  Protein file size exceeds permitted ${maxFileSizeByWget} bytes. Abort job.\n"
						def errorStrMsg = "Hello!\nYour AUGUSTUS training job ${trainingInstance.accession_id} for species ${trainingInstance.project_name} was aborted because the protein file size was with ${protein_size} bigger than 1 GB. Please submitt a smaller protein size!\n\nBest regards,\n\nthe AUGUSTUS web server team\n\nhttp://bioinf.uni-greifswald.de/trainaugustus\n"
						sendMail {
								to "${trainingInstance.email_adress}"
								subject "Your AUGUSTUS training job ${trainingInstance.accession_id} was aborted"
								body """${errorStrMsg}"""
						}
						// delete database entry
						trainingInstance.delete()
						cmdStr = "rm -r ${projectDir} &> /dev/null"
						delProc = "${cmdStr}".execute()
						if(verb > 1){
							logDate = new Date()
							logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
						}
						delProc.waitFor()
						return
					}
					if(proteinFastaFlag == 1) {
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v1 -  The protein file was not protein fasta. ${projectDir} is deleted.\n"
						cmdStr = "rm -r ${projectDir} &> /dev/null"
						delProc = "${cmdStr}".execute()
						if(verb > 1){
							logDate = new Date()
							logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
						}
						delProc.waitFor()
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v1 -  Job ${trainingInstance.accession_id} by user ${trainingInstance.email_adress} is aborted!\n"
						sendMail {
							to "${trainingInstance.email_adress}"
							subject "Your AUGUSTUS training job ${trainingInstance.accession_id} was aborted"
							body """Hello!

Your AUGUSTUS training job ${trainingInstance.accession_id} for species ${trainingInstance.project_name} was aborted because the provided protein file ${trainingInstance.protein_ftp_link} is not in fasta format.

Best regards,

the AUGUSTUS web server team

http://bioinf.uni-greifswald.de/trainaugustus
"""
						}
						// delete database entry
						trainingInstance.delete()
						return
					}
					def proteinCksumScript = new File("${projectDir}/protein_cksum.sh")
					def proteinCksumFile = "${projectDir}/protein.cksum"
					cmd2Script = "cksum ${projectDir}/protein.fa > ${proteinCksumFile} 2> /dev/null"
					proteinCksumScript << "${cmd2Script}"
					if(verb > 2){
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v3 - proteinCksumScript << \"${cmd2Script}\"\n"
					}
					cmdStr = "bash ${projectDir}/protein_cksum.sh"
					def proteinCksumProcess = "${cmdStr}".execute()
					if(verb > 1){
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
					}
					proteinCksumProcess.waitFor()
					def proteinCksumContent = new File("${proteinCksumFile}").text
					def proteinCksum_array = proteinCksumContent =~/(\d*) \d* /
					def proteinCksum
					(1..proteinCksum_array.groupCount()).each{proteinCksum = "${proteinCksum_array[0][it]}"}
					trainingInstance.protein_cksum = "${proteinCksum}"
					proteinCksum_array = proteinCksumContent =~/\d* (\d*) /
					trainingInstance.protein_size
					(1..proteinCksum_array.groupCount()).each{trainingInstance.protein_size = "${proteinCksum_array[0][it]}"}
					logDate = new Date()
					logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - protein.fa is ${trainingInstance.protein_size} big and has a cksum of ${proteinCksum}.\n"
					cmdStr = "rm ${projectDir}/protein.cksum &> /dev/null"
					delProc = "${cmdStr}".execute()
					if(verb > 1){
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
					}
					delProc.waitFor()
					cmdStr = "rm ${projectDir}/protein_cksum.sh &> /dev/null"
					delProc = "${cmdStr}".execute()
					if(verb > 1){
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
					}
					delProc.waitFor()
					proteinExistsFlag = 1
				} // end of (!(trainingInstance.protein_ftp_link == null))

				// confirm file upload via e-mail
				if((!(trainingInstance.genome_ftp_link == null)) || (!(trainingInstance.protein_ftp_link == null)) || (!(trainingInstance.est_ftp_link == null))){
					sendMail {
						to "${trainingInstance.email_adress}"
						subject "File upload has been completed for AUGUSTUS training job ${trainingInstance.accession_id}"
						body """Hello!

We have retrieved all files that you specified, successfully. You may delete them from the public server, now, without affecting the AUGUSTUS training job.

Best regards,

the AUGUSTUS web server team

http://bioinf.uni-greifswald.de/trainaugustus
"""
					}
				}

				// File formats appear to be ok. 
				// check whether this job was submitted before:
				def grepScript = new File("${projectDir}/grepScript.sh")
				def grepResult = "${projectDir}/grep.result"
				cmd2Script = "grep \"\\(Genome-Cksum: \\[${trainingInstance.genome_cksum}\\] Genome-Filesize: \\[${trainingInstance.genome_size}\\]\\)\" ${dbFile} | grep \"\\(EST-Cksum: \\[${trainingInstance.est_cksum}\\] EST-Filesize: \\[${trainingInstance.est_size}\\]\\)\" | grep \"\\(Protein-Cksum: \\[${trainingInstance.protein_cksum}\\] Protein-Filesize: \\[${trainingInstance.protein_size}\\]\\)\" | grep \"\\(Struct-Cksum: \\[${trainingInstance.struct_cksum}\\]\\)\" > ${grepResult} 2> /dev/null"
				grepScript << "${cmd2Script}"
				if(verb > 2){
					logDate = new Date()
					logFile <<  "${logDate} ${trainingInstance.accession_id} v3 - grepScript << \"${cmd2Script}\"\n"
				}
				cmdStr = "bash ${projectDir}/grepScript.sh"
				def grepJob = "${cmdStr}".execute()
				if(verb > 1){
					logDate = new Date()
					logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
				}
				grepJob.waitFor()
				def grepContent = new File("${grepResult}").text
				if(grepContent =~ /Struct-Cksum/){
					//job was submitted before. Send E-Mail to user with a link to the results.
					def id_array = grepContent =~ /Grails-ID: \[(\d*)\] /
					oldID
					(0..id_array.groupCount()).each{oldID = "${id_array[0][it]}"}
					def oldAccScript = new File("${projectDir}/oldAcc.sh")
					def oldAccResult = "${projectDir}/oldAcc.result"
					cmd2Script = "grep \"Grails-ID: \\[${oldID}\\]\" ${dbFile} | perl -ne \"@t = split(/\\[/); @t2 = split(/\\]/, \\\$t[4]); print \\\$t2[0];\" > ${oldAccResult} 2> /dev/null"
					oldAccScript << "${cmd2Script}"
					if(verb > 2){
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v3 - oldAccScript << \"${cmd2Script}\"\n"
					}
					cmdStr = "bash ${projectDir}/oldAcc.sh"
					def oldAccScriptProc = "${cmdStr}".execute()
					if(verb > 1){
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
					}
					oldAccScriptProc.waitFor()
					def oldAccContent = new File("${oldAccResult}").text	      
					sendMail {
						to "${trainingInstance.email_adress}"
						subject "Your AUGUSTUS training job ${trainingInstance.accession_id} was submitted before as job ${oldAccContent}"
						body """Hello!

You submitted job ${trainingInstance.accession_id} for species ${trainingInstance.project_name}. The job was aborted because the files that you submitted were submitted, before. 

Details of your job:
${confirmationString}

The job status of the previously submitted job is available at http://bioinf.uni-greifswald.de/augustus-training-0.1/training/show/${oldID}

The results are available at http://bioinf.uni-greifswald.de/trainaugustus/training-results/${oldAccContent}/index.html (Results are only available in case the previously submitted job's computations have finished, already.)

Thank you for using AUGUSTUS!

Best regards,

the AUGUSTUS web server team

http://bioinf.uni-greifswald.de/trainaugustus
"""
					}
					logDate = new Date()
					logFile <<  "${logDate} ${trainingInstance.accession_id} v1 -  Data are identical to old job ${oldAccContent} with Accession-ID ${oldAccContent}. ${projectDir} is deleted.\n"
					cmdStr = "rm -r ${projectDir} &> /dev/null"
					//delProc = "${cmdStr}".execute()
					if(verb > 1){
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
					}
					//delProc.waitFor()
					logDate = new Date()
					logFile <<  "${logDate} ${trainingInstance.accession_id} v1 -  Job ${trainingInstance.accession_id} by user ${trainingInstance.email_adress} is aborted, the user is informed!\n"
					trainingInstance.delete()
					return
				} // end of job was submitted before check
				//Write DB file: 
				dbFile << "Date: [${today}] User-IP: [${userIP}] Grails-ID: [${trainingInstance.id}] Accession-ID: [${trainingInstance.accession_id}] Genome-File: [${trainingInstance.genome_file}] Genome-FTP-Link: [${trainingInstance.genome_ftp_link}] Genome-Cksum: [${trainingInstance.genome_cksum}] Genome-Filesize: [${trainingInstance.genome_size}] EST-File: [${trainingInstance.est_file}] EST-FTP-Link: [${trainingInstance.est_ftp_link}] EST-Cksum: [${trainingInstance.est_cksum}] EST-Filesize: [${trainingInstance.est_size}] Protein-File: [${trainingInstance.protein_file}] Protein-FTP-Link: [${trainingInstance.protein_ftp_link}] Protein-Cksum: [${trainingInstance.protein_cksum}] Protein-Filesize: [${trainingInstance.protein_size}] Training-Structure-File: [${trainingInstance.struct_file}] Struct-Cksum: [${trainingInstance.struct_cksum}] Struct-Filesize: [${trainingInstance.struct_size}]\n"
				//Create a test sge script:
				logDate = new Date()
				logFile <<  "${logDate} ${trainingInstance.accession_id} v1 -  Writing SGE submission script.\n"
				def sgeFile = new File("${projectDir}/augtrain.sh")
				// write command in script (according to uploaded files)
				sgeFile << "#!/bin/bash\n#\$ -S /bin/bash\n#\$ -cwd\n\n"
				// this has been checked, works.
				if( estExistsFlag ==1 && proteinExistsFlag == 0 && structureExistsFlag == 0){
					cmd2Script = "autoAug.pl --genome=${projectDir}/genome.fa --species=${trainingInstance.accession_id} --cdna=${projectDir}/est.fa --pasa -v --singleCPU --workingdir=${projectDir} > ${projectDir}/AutoAug.log 2> ${projectDir}/AutoAug.err\n\nwriteResultsPage.pl ${trainingInstance.accession_id} ${trainingInstance.project_name} ${dbFile} ${output_dir} ${web_output_dir} ${AUGUSTUS_CONFIG_PATH} ${AUGUSTUS_SCRIPTS_PATH}  1 > ${projectDir}/writeResults.log 2> ${projectDir}/writeResults.err"
					sgeFile << "${cmd2Script}"
					if(verb > 2){
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v3 - sgeFile << \"${cmd2Script}\"\n"
					}
					// this is currently tested
				}else if(estExistsFlag == 0 && proteinExistsFlag == 0 && structureExistsFlag == 1){
					cmd2Script = "autoAug.pl --genome=${projectDir}/genome.fa --species=${trainingInstance.accession_id} --trainingset=${projectDir}/training-gene-structure.gff -v --singleCPU --workingdir=${projectDir} > ${projectDir}/AutoAug.log 2> ${projectDir}/AutoAug.err\n\nwriteResultsPage.pl ${trainingInstance.accession_id} ${trainingInstance.project_name} ${dbFile} ${output_dir} ${web_output_dir} ${AUGUSTUS_CONFIG_PATH} ${AUGUSTUS_SCRIPTS_PATH} 1 > ${projectDir}/writeResults.log 2> ${projectDir}/writeResults.err"
					sgeFile << "${cmd2Script}"
					// this is currently tested
				}else if(estExistsFlag == 0 && proteinExistsFlag == 1 && structureExistsFlag == 0){
					cmd2Script = "autoAug.pl --genome=${projectDir}/genome.fa --species=${trainingInstance.accession_id} --trainingset=${projectDir}/protein.fa -v --singleCPU --workingdir=${projectDir} > ${projectDir}/AutoAug.log 2> ${projectDir}/AutoAug.err\n\nwriteResultsPage.pl ${trainingInstance.accession_id} ${trainingInstance.project_name} ${dbFile} ${output_dir} ${web_output_dir} ${AUGUSTUS_CONFIG_PATH} ${AUGUSTUS_SCRIPTS_PATH} > ${projectDir}/writeResults.log 1 2> ${projectDir}/writeResults.err"
					sgeFile << "${cmd2Script}"
					if(verb > 2){
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v3 - sgeFile << \"${cmd2Script}\"\n"
					}
					// all following commands still need testing
				}else if(estExistsFlag == 1 && proteinExistsFlag == 1 && structureExistsFlag == 0){
					cmd2Script = "autoAug.pl --genome=${projectDir}/genome.fa --species=${trainingInstance.accession_id} --cdna=${projectDir}/est.fa --trainingset=${projectDir}/protein.fa -v --singleCPU --workingdir=${projectDir} > ${projectDir}/AutoAug.log 2> ${projectDir}/AutoAug.err\n\nwriteResultsPage.pl ${trainingInstance.accession_id} ${trainingInstance.project_name} ${dbFile} ${output_dir} ${web_output_dir} ${AUGUSTUS_CONFIG_PATH} ${AUGUSTUS_SCRIPTS_PATH} 1 > ${projectDir}/writeResults.log 2> ${projectDir}/writeResults.err"
					sgeFile << "${cmd2Script}"
					if(verb > 2){
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v3 - sgeFile << \"${cmd2Script}\"\n"
					}
				}else if(estExistsFlag == 1 && proteinExistsFlag == 0 && structureExistsFlag == 1){
					cmd2Script = "autoAug.pl --genome=${projectDir}/genome.fa --species=${trainingInstance.accession_id} --cdna=${projectDir}/est.fa --trainingset=${projectDir}/training-gene-structure.gff -v --singleCPU --workingdir=${projectDir} > ${projectDir}/AutoAug.log 2> ${projectDir}/AutoAug.err\n\nwriteResultsPage.pl ${trainingInstance.accession_id} ${trainingInstance.project_name} ${dbFile} ${output_dir} ${web_output_dir} ${AUGUSTUS_CONFIG_PATH} ${AUGUSTUS_SCRIPTS_PATH} 1 > ${projectDir}/writeResults.log 2> ${projectDir}/writeResults.err"
					sgeFile << "${cmd2Script}"
					if(verb > 2){
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v3 - sgeFile << \"${cmd2Script}\"\n"
					}
				}else if(estExistsFlag == 0 && proteinExistsFlag == 1 && structureExistsFlag == 1){
					sgeFile << "echo 'Simultaneous protein and structure file support are currently not implemented. Using the structure file, only.'\n\nautoAug.pl --genome=${projectDir}/genome.fa --species=${trainingInstance.accession_id} --trainingset=${projectDir}/training-gene-structure.gff -v --singleCPU --workingdir=${projectDir} > ${projectDir}/AutoAug.log 2> ${projectDir}/AutoAug.err\n\nwriteResultsPage.pl ${trainingInstance.accession_id} ${trainingInstance.project_name} ${dbFile} ${output_dir} ${web_output_dir} ${AUGUSTUS_CONFIG_PATH} ${AUGUSTUS_SCRIPTS_PATH} 1 > ${projectDir}/writeResults.log 2> ${projectDir}/writeResults.err"
					if(verb > 2){
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v3 - sgeFile << \"${cmd2Script}\"\n"
					}
				}else if(estExistsFlag == 1 && proteinExistsFlag == 1 && structureExistsFlag == 1){
					cmd2Script = "echo Simultaneous protein and structure file support are currently not implemented.\n\nUsing the structure file, only.'\n\nautoAug.pl --genome=${projectDir}/genome.fa --species=${trainingInstance.accession_id} --trainingset=${projectDir}/training-gene-structure.gff -v --singleCPU --workingdir=${projectDir} > ${projectDir}/AutoAug.log 2> ${projectDir}/AutoAug.err\n\nwriteResultsPage.pl ${trainingInstance.accession_id} ${trainingInstance.project_name} ${dbFile} ${output_dir} ${web_output_dir} ${AUGUSTUS_CONFIG_PATH} ${AUGUSTUS_SCRIPTS_PATH} 1 > ${projectDir}/writeResults.log 2> ${projectDir}/writeResults.err"
					sgeFile << "${cmd2Script}"
					if(verb > 2){
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v3 - sgeFile << \"${cmd2Script}\"\n"
					}
				}else{
					logDate = new Date()
					logFile <<  "${logDate} ${trainingInstance.accession_id} v1 -  EST: ${estExistsFlag} Protein: ${proteinExistsFlag} Structure: ${structureExistsFlag} SGE-script remains empty! This an error that should not be possible.\n"
				}
				// write submission script
				def submissionScript = new File("${projectDir}/submitt.sh")
				def fileID = "${projectDir}/jobID"
				cmd2Script = "cd ${projectDir}; /usr/bin/qsub augtrain.sh > ${fileID} 2> /dev/null"
				submissionScript << "${cmd2Script}"
				if(verb > 2){
					logDate = new Date()
					logFile <<  "${logDate} ${trainingInstance.accession_id} v3 - submissionScript << \"${cmd2Script}\"\n"
				}
				// submitt job
				cmdStr = "bash ${projectDir}/submitt.sh"
				def jobSubmission = "${cmdStr}".execute()
				if(verb > 1){
					logDate = new Date()
					logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
				}
				jobSubmission.waitFor()
				// get job ID
				content = new File("${fileID}").text
				def jobID_array = content =~/Your job (\d*)/
				def jobID
				(1..jobID_array.groupCount()).each{jobID = "${jobID_array[0][it]}"}
				trainingInstance.job_id = jobID
				logDate = new Date()
				logFile <<  "${logDate} ${trainingInstance.accession_id} v1 -  Job ${jobID} submitted.\n"
				// check for job status
				trainingInstance.job_status = 1 // submitted
				trainingInstance = trainingInstance.merge()
				trainingInstance.save()
				def statusScript = new File("${projectDir}/status.sh")
				def statusFile = "${projectDir}/job.status"
				cmd2Script = "cd ${projectDir}; /usr/bin/qstat -u \"*\" |grep augtrain |grep ${jobID} > ${statusFile} 2> /dev/null"
				statusScript << "${cmd2Script}"
				if(verb > 2){
					logDate = new Date()
					logFile <<  "${logDate} ${trainingInstance.accession_id} v3 - statusScript << \"${cmd2Script}\"\n"
				}
				def statusContent
				def statusCheck 
				def qstat = 1
				def runFlag = 0;
				logDate = new Date()
				logFile <<  "${logDate} ${trainingInstance.accession_id} v1 - checking job SGE status...\n"
				while(qstat == 1){
					sleep(300000) // 5 minutes
					statusCheck = "bash ${projectDir}/status.sh".execute()
					statusCheck.waitFor()
					statusContent = new File("${statusFile}").text
					if(statusContent =~ /qw/){ 
						trainingInstance.job_status = 2 
						trainingInstance = trainingInstance.merge()
						trainingInstance.save()
					}else if( statusContent =~ /  r  / ){
						trainingInstance.job_status = 3
						trainingInstance = trainingInstance.merge()
						trainingInstance.save()
						if(runFlag == 0){
							today = new Date() 
							logDate = new Date()
							logFile <<  "${logDate} ${trainingInstance.accession_id} v1 -  Job ${jobID} begins running at ${today}.\n"
						}
						runFlag = 1
					}else{
						trainingInstance.job_status = 4
						trainingInstance = trainingInstance.merge()
						trainingInstance.save()
						qstat = 0
						today = new Date()
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v1 -  Job ${jobID} left SGE at ${today}.\n"
					}
			   	}
				logDate = new Date()
				logFile <<  "${logDate} ${trainingInstance.accession_id} v1 -  Job status is ${trainingInstance.job_status} when job leaves SGE.\n"

			   	// check whether errors occured by log-file-sizes
				logDate = new Date()
				logFile <<  "${logDate} ${trainingInstance.accession_id} v1 -  Beginning to look for errors.\n"
				if(new File("${projectDir}/AutoAug.err").exists()){
					autoAugErrSize = new File("${projectDir}/AutoAug.err").size()
					logDate = new Date()
					logFile <<  "${logDate} ${trainingInstance.accession_id} v1 -  autoAugErrorSize is ${autoAugErrSize}.\n"
				}else{
					logDate = new Date()
					logFile << "SEVERE ${logDate} ${trainingInstance.accession_id} v1 -  autoAugError file was not created. Default size value is set to 10.\n"
					autoAugErrSize = 10
				}
				if(new File("${projectDir}/augtrain.sh.e${jobID}").exists()){
					sgeErrSize = new File("${projectDir}/augtrain.sh.e${jobID}").size()
					logDate = new Date()
					logFile <<  "${logDate} ${trainingInstance.accession_id} v1 -  sgeErrSize is ${sgeErrSize}.\n"

				}else{
					logDate = new Date()					
					logFile << "SEVERE ${logDate} ${trainingInstance.accession_id} v1 -   sgeErr file was not created. Default size value is set to 10.\n"
					sgeErrSize = 10
				}
				if(new File("${projectDir}/writeResults.err").exists()){
					writeResultsErrSize = new File("${projectDir}/writeResults.err").size()
					logDate = new Date()
					logFile <<  "${logDate} ${trainingInstance.accession_id} v1 -  writeResultsSize is ${writeResultsErrSize}.\n"
				}else{
					logDate = new Date()
					logFile << "SEVERE ${logDate} ${trainingInstance.accession_id} v1 -   writeResultsErr file was not created. Default size value is set to 10.\n"
					writeResultsErrSize = 10
				}
				if(autoAugErrSize==0 && sgeErrSize==0 && writeResultsErrSize==0){
					logDate = new Date()
					logFile <<  "${logDate} ${trainingInstance.accession_id} v1 -  no errors occured (option 1).\n"
					sendMail {
						to "${trainingInstance.email_adress}"
						subject "Your AUGUSTUS training job ${trainingInstance.accession_id} is complete"
						body """Hello!

Your AUGUSTUS training job ${trainingInstance.accession_id} for species ${trainingInstance.project_name} is complete. You find the results at http://bioinf.uni-greifswald.de/trainaugustus/training-results/${trainingInstance.accession_id}/index.html .

Thank you for using AUGUSTUS!

Best regards,

the AUGUSTUS web server team

http://bioinf.uni-greifswald.de/trainaugustus
"""
					}
					logDate = new Date()
					logFile <<  "${logDate} ${trainingInstance.accession_id} v1 -  Sent confirmation Mail that job computation was successful.\n"
					// Do NOT use 7z because webserver user will pack the tar, and even if you later change permissions for the 7z, you cannot change permissions for the underlying tar, and such, you will never be able to access the archive as another user
					// unpack with 7z x XA2Y5VMJ.tar.7z
					// tar xvf XA2Y5VMJ.tar
					def packResults = new File("${output_dir}/pack${trainingInstance.accession_id}.sh")
					cmd2Script = "cd ${output_dir}; tar -czvf ${trainingInstance.accession_id}.tar.gz ${trainingInstance.accession_id} &> /dev/null"
					packResults << "${cmd2Script}"
					if(verb > 2){
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v3 - packResults << \"${cmd2Script}\"\n"
					}
					//packResults << "cd ${output_dir}; tar cf - ${trainingInstance.accession_id} | 7z a -si ${trainingInstance.accession_id}.tar.7z; rm -r ${trainingInstance.accession_id};"
					cmdStr = "bash ${output_dir}/pack${trainingInstance.accession_id}.sh"
					def cleanUp = "${cmdStr}".execute()
					if(verb > 1){
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
					}
					cleanUp.waitFor()
					cmdStr = "rm ${output_dir}/pack${trainingInstance.accession_id}.sh &> /dev/null"
					cleanUp = "${cmdStr}".execute()
					if(verb > 1){
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
					}
					cmdStr = "rm -r ${projectDir} &> /dev/null"
					delProc = "${cmdStr}".execute()
					if(verb > 1){
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
					}
            				delProc.waitFor()
					logDate = new Date()
					logFile <<  "${logDate} ${trainingInstance.accession_id} v1 -  autoAug directory was packed with tar/gz.\n"
					//logFile << "${trainingInstance.accession_id} v1 -  autoAug directory was packed with tar/7z.\n"
					logDate = new Date()
					logFile <<  "${logDate} ${trainingInstance.accession_id} v1 -  Job completed. Result: ok.\n"
				}else{
					logDate = new Date()
					logFile <<  "${logDate} ${trainingInstance.accession_id} v1 -  an error occured somewhere.\n"
					if(!(autoAugErrSize == 0)){
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v1 -  an error occured when autoAug.pl was executed!\n"; 
						sendMail {
						to "${admin_email}"
						subject "Error in AUGUSTUS training job ${trainingInstance.accession_id}"
						body """Hi ${admin_email}!

Job: ${trainingInstance.accession_id}
E-Mail: ${trainingInstance.email_adress}
Species: ${trainingInstance.project_name}
Link: http://bioinf.uni-greifswald.de/trainaugustus/training-results/${trainingInstance.accession_id}/index.html

An error occured in the autoAug pipeline. Please check manually what's wrong. The user has been informed.
"""
						}
						trainingInstance.job_error = 5
						trainingInstance = trainingInstance.merge()
						trainingInstance.save()
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v1 -  Job status is ${trainingInstance.job_error} when autoAug error occured.\n"
						def packResults = new File("${output_dir}/pack${trainingInstance.accession_id}.sh")
						cmd2Script = "cd ${output_dir}; tar -czvf ${trainingInstance.accession_id}.tar.gz ${trainingInstance.accession_id} &> /dev/null"
						packResults << "${cmd2Script}"
						if(verb > 2){
							logDate = new Date()
							logFile <<  "${logDate} ${trainingInstance.accession_id} v3 - packResults << \"${cmd2Script}\"\n"
						}
						//packResults << "cd ${output_dir}; tar cf - ${trainingInstance.accession_id} | 7z a -si ${trainingInstance.accession_id}.tar.7z; rm -r ${trainingInstance.accession_id};"
						cmdStr = "bash ${output_dir}/pack${trainingInstance.accession_id}.sh"
						def cleanUp = "${cmdStr}".execute()
						if(verb > 1){
							logDate = new Date()
							logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
						}
						cleanUp.waitFor()
						cmdStr = "rm ${output_dir}/pack${trainingInstance.accession_id}.sh &> /dev/null"
						cleanUp = "${cmdStr}".execute()
						if(verb > 1){
							logDate = new Date()
							logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
						}
						cmdStr = "rm -r ${projectDir} &> /dev/null"
						delProc = "${cmdStr}".execute()
						if(verb > 1){
							logDate = new Date()
							logFile <<  "${logDate} ${trainingInstance.accession_id} v2 - \"${cmdStr}\"\n"
						}
            					delProc.waitFor()
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v1 -  autoAug directory was packed with tar/gz.\n"
						//logFile << "${trainingInstance.accession_id} v1 -  autoAug directory was packed with tar/7z.\n"
					}
					if(!(sgeErrSize == 0)){
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v1 -  a SGE error occured!\n";
						sendMail {
						to "${admin_email}"
						subject "Error in AUGUSTUS training job ${trainingInstance.accession_id}"
						body """Hi ${admin_email}!

Job: ${trainingInstance.accession_id}
E-Mail: ${trainingInstance.email_adress}
Species: ${trainingInstance.project_name}
Link: http://bioinf.uni-greifswald.de/trainaugustus/training-results/${trainingInstance.accession_id}/index.html

An SGE error occured. Please check manually what's wrong. The user has been informed.
"""
						}
						trainingInstance.job_error = 5
						trainingInstance = trainingInstance.merge()
						trainingInstance.save()
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v1 -  Job status is ${trainingInstance.job_error} when SGE error occured.\n"
					}
					if(!(writeResultsErrSize == 0)){
						logDate = new Date()
						logFile <<  "${logDate} ${trainingInstance.accession_id} v1 -  an error occured during writing results!\n";
						sendMail {
							to "${admin_email}"
							subject "Error in AUGUSTUS training job ${trainingInstance.accession_id}"
							body """Hi ${admin_email}!

Job: ${trainingInstance.accession_id}
E-Mail: ${trainingInstance.email_adress}
Species: ${trainingInstance.project_name}
Link: http://bioinf.uni-greifswald.de/trainaugustus/training-results/${trainingInstance.accession_id}/index.html

An error occured during writing results. Please check manually what's wrong. The user has been informed.
"""
						}
					}
					logDate = new Date()
					logFile <<  "${logDate} ${trainingInstance.accession_id} v1 -  Job status is ${trainingInstance.job_error} after all errors have been checked.\n"
					sendMail {
					to "${trainingInstance.email_adress}"
						subject "A problem occured during execution of your AUGUSTUS training job ${trainingInstance.accession_id}"
						body """Hello!

A problem occured while training AUGUSTUS for species ${trainingInstance.project_name} (Job ${trainingInstance.accession_id}).

You find the results page of your job at http://bioinf.uni-greifswald.de/trainaugustus/training-results/${trainingInstance.accession_id}/index.html. Please check the provided log-files carefully before proceeding to work with the produced results. Please contact us (augustus-web@uni-greifswald.de) in case you are in doubt about the results.

Thank you for using AUGUSTUS!

Best regards,

the AUGUSTUS web server team

http://bioinf.uni-greifswald.de/trainaugustus
"""
					}
				}
			}
			//------------ END BACKGROUND PROCESS ----------------------------------

		}
	}
}
