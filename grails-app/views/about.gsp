<!DOCTYPE html>
<html>
	<head>
		<meta name="layout" content="main"/>
		<title>Bioinformatics Web Server - University of Greifswald</title>
        <meta name="date" content="2018-07-17">
        <meta name="lastModified" content="2018-07-16">
	</head>
	<body>
               <main class="main-content">
                  <div id="c180465" class="csc-default">
                     <div class="csc-header csc-header-n1">
                        <h1 class="csc-firstHeader">About AUGUSTUS</h1>
                     </div>
                  </div>
                  <div id="c261665" class="csc-default">
                     <div class="csc-default">
                        <div class="divider">
                           <hr>
                        </div>
                     </div>
                     <p align="center"><a href="${createLink(uri:'/index')}"><g:img dir="images" file="augustus.head.transparent.gif" alt="" width="93" height="100" />
                     </a></p>
                     <p>AUGUSTUS is a program that predicts genes in eukaryotic genomic sequences. It can be <a href="/webaugustus/prediction/create">run with pre-trained parameters on this web server</a> or be downloaded and <a href="//bioinf.uni-greifswald.de/augustus/binaries/">run locally</a>. It is open source so you can compile it for your computing platform. You can also run AUGUSTUS on the German <a href="http://www.medigrid.de/anw_bio.html">MediGRID</a>. This enables you to submit larger sequence files and allows to use protein homology information in the prediction. The MediGRID requires an instant easy registration by email for first-time users.</p>
                     <p>The <a href="/webaugustus/training/create">AUGUSTUS training web server application</a> automatically generates training gene sets from genomic sequence(s) and a set of Proteins or ESTs and subsequently trains AUGUSTUS parameters for a new species, and runs gene predictions with the new parameters and the supplied extrinsic evidence. Alternatively, you can also download the <a href="//bioinf.uni-greifswald.de/augustus/binaries/">autoAug.pl pipeline</a> and execute our training and prediction pipeline locally.</p>
                     <br>
                     <h2>Features</h2>
                     <br>
                     <h3>Gene prediction with AUGUSTUS</h3>
                     <ul>
                        <li>AUGUSTUS now has a protein profile extension (PPX) which allows to use protein family specific conservation in order to identify members and their exon-intron structure of a protein family given by a block profile. The block profile can be constructed with accompanying scripts from a multiple protein sequence alignment. For more details please refer to the <a href="//bioinf.uni-greifswald.de/augustus/binaries/README.md">README.md</a>.</li>
                        <li>The download version of AUGUSTUS can incorporate data from RNA-Seq (short cDNA reads, single or paired-end, e.g. from Illumina or SOLiD) as documented <a href="//bioinf.uni-greifswald.de/augustus/binaries/readme.rnaseq.html">here</a> (January 8, 2012).</li>
                        <li>If you use the <a href="//bioinf.uni-greifswald.de/augustus/">old AUGUSTUS web server application</a>, the results can be displayed automatically in the genome browser Gbrowse. You can browse the gene predictions together with the input sequence, the constraints and the cDNA alignments. Gbrowse also enables you to simultaneously display your own annotation and to export the image in scalable vector graphics format</li>
                        <li>
                           You can upload cDNA sequences together with the genomic DNA. Your ESTs or mRNA will be used to improve the gene prediction.
                           <p align="center"><a href="${resource( dir:'images', file:'AUG.cDNA.gif' )}"><g:img dir="images" file="AUG.cDNA.gif"  alt="with cDNA" width="70%" /></a><br><i>Click on image to enlarge!</i></p>
                        </li>
                        <li> AUGUSTUS usually belongs to the most accurate programs for the species it is trained
                           for. Often it is the most accurate <i>ab initio</i> program. For example, at the independent gene finder
                           assessment (EGASP) on the human ENCODE regions AUGUSTUS was the most accurate gene finder among the
                           tested <i>ab initio</i> programs. At the more recent nGASP (worm), it was among the best in the <i>ab initio</i>
                           and transcript-based categories. See <a href="${createLink(uri:'/accuracy')}">accuracy statistics</a> for further statics.
                        </li>
                        <li>
                           AUGUSTUS can be used <i>ab initio</i> and has a flexible mechanism for incorporating extrinsic
                           information, e.g. from EST alignments and protein alignments. Here is an example from the UCSC
                           Genome Browser where the AUGUSTUS prediction incorporates mRNA alignments, EST alignments,
                           conservation and other sources of information:<br>
                           <p align="center"><a href="${resource( dir:'images', file:'hints.gif' )}"><g:img dir="images" file="hints.gif"  alt="hints" width="70%" /></a><br>
                              <i>Click on image to enlarge!</i>
                           </p>
                        </li>
                        <li>
                           AUGUSTUS can predict alternative splicing and alternative transcripts. It can do this for example
                           when the EST alignments suggest alternative splicing like in this example:<br>
                           <p align="center"><a href="${resource( dir:'images', file:'alternative.gif' )}"><g:img dir="images" file="alternative.gif"  alt="alternative" width="40%" /></a><br>
                              <i>Click on image to enlarge!</i>
                           </p>
                        </li>
                        <li> AUGUSTUS can predict the 5'UTR and 3'UTR including introns. This is in particular helpful when
                           using EST alignments as the majority of ESTs aligns in the untranslated regions (<a href="${resource( dir:'images', file:'alternative.gif' )}">example</a>).
                           This feature is currently only trained for human, the red algae <i>Galdieria sulphuraria</i>, <i>Caenorhabditis elegans</i>,
                           <i>Toxoplasma gondii</i>, <i>Chlamydomonas reinhardtii</i>, pea aphid, <i>Culex pipens</i> (3'UTR only), butterfly, <i>Bombus terrestris/impatiens</i>, chlorella,
                           elephant shark, honeybee, <i>Leishmania tarentolae</i>, maize, rhodius, tomato, trichinella.
                        </li>
                        <li> AUGUSTUS can report a large number of alternative genes, including probabilities for the
                           transcripts and each exon and intron. You can make AUGUSTUS predict suboptimal gene structures 
													(<a href="${resource( dir:'images', file:'alternative-sampling.png' )}">example</a>) and you can adjust command line parameters to regulate
                           the number of reported alternatives.
                        </li>
                     </ul>
                     <br>
                     <h3>Training AUGUSTUS</h3>
                     <ul>
                        <li> AUGUSTUS is retrainable. It comes with a <a href="//bioinf.uni-greifswald.de/augustus/binaries/">training program</a> that estimates the parameters given a training set of known genes. It also comes with an optimization script that tries to find values for the meta parameters, like splice window sizes, that optimize the prediction accuracy. The training program is also available through a <a href="/webaugustus/training/create">web interface</a>.
                        </li>
                     </ul>
                     <br>
                     <h3>Parameters are already available for the following species:</h3>
                     <p>AUGUSTUS has currently been trained on species specific training sets to predict genes in the
                     following species. Note that for closely related species usually only one version is necessary. For
                     example, the human version is good for all mammals. <a href="${createLink(uri:'/references#contrib')}">Contributions</a>.
                     </p>
                     <table border="0" cellspacing="10" align="center">
                        <tr>
                           <th>animals:&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</th>
                           <th>alveolata:&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</th>
                           <th>plants and algae:&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</th>
                           <th>fungi:</th>
                        </tr>
                        <tr>
                           <td valign="top"><font size="-2">
                              <i>Acyrthosiphon pisum</i><br>
                              <i>Aedes aegypti</i><br>
                              <i>Amphimedon queenslandica</i><br>
                              <i>Apis mellifera</i><br>
                              <i>Brugia malayi</i><br>
                              <i>Caenorhabditis elegans</i><br>
                              <i>Callorhinchus milii</i><br>
                              <i>Culex pipiens</i><br>
                              <i>Drosophila melagonaster</i><br>
                              <i>Homo sapiens</i><br>
                              <i>Nasonia vitripennis</i><br>
                              <i>Petromyzon marinus</i><br>
                              <i>Schistosoma mansoni</i><br>
                              <i>Tribolium castaneum</i><br>
                              </font>
                           </td>
                           <td valign="top"><font size="-2">
                              <i>Tetrahymena thermophila</i><br>
                              <i>Toxoplasma gondii</i><br>
                              </font>
                           </td>
                           <td valign="top"><font size="-2">
                              <i>Arabidopsis thaliana</i><br>
                              <i>Chlamydomonas reinhardtii</i><br>
                              <i>Galdieria sulphuraria</i><br>
                              (<i>Nicotiana tabacum</i>)<br>
                              <i>Solanum lycopersicum</i><br>
                              <i>Zea mays</i><br>
                              </font>
                           </td>
                           <td valign="top"><font size="-2">
                              <i>Aspergillus fumigatus</i><br>
                              <i>Aspergillus nidulans</i><br>
                              <i>Aspergillus oryzae</i><br>
                              <i>Aspergillus terreus</i><br>
                              <i>Botrytis cinerea</i><br>
                              <i>Candida albicans</i><br>
                              <i>Candida guilliermondii</i><br>
                              <i>Candida tropicalis</i><br>
                              <i>Chaetomium globosum</i><br>
                              <i>Coccidioides immitis</i><br>
                              <i>Coprinus cinereus</i><br>
                              <i>Cryptococcus neoformans</i><br>
                              <i>Debaryomyces hansenii</i><br>
                              <i>Encephalitozoon cuniculi</i><br>
                              <i>Eremothecium gossypii</i><br>
                              <i>Fusarium graminearum</i><br>
                              <i>Histoplasma capsulatum</i><br>
                              <i>Kluyveromyces lactis</i><br>
                              <i>Laccaria bicolor</i><br>
                              <i>Lodderomyces elongisporus</i><br>
                              <i>Magnaporthe grisea</i><br>
                              <i>Neurospora crassa</i><br>
                              <i>Phanerochaete chrysosporium</i><br>
                              <i>Pichia stipitis</i><br>
                              <i>Rhizopus oryzae</i><br>
                              <i>Pneumocystis jirovecii</i><br>
                              <i>Saccharomyces cerevisiae</i><br>
                              <i>Schizosaccharomyces pombe</i><br>
                              <i>Ustilago maydis</i><br>
                              <i>Verticillium longisporum</i><br>
                              <i>Yarrowia lipolytica</i><br>
                              </font>
                           </td>
                        </tr>
                     </table>
                     <br>
                     Examples of AUGUSTUS predictions can be viewed at at various genome browsers:<br><br>
                     <a href="http://www.genome.ucsc.edu/cgi-bin/hgGateway">UCSC Genome Browser</a>,<br>
                     Wormbase: 
                     <a href="https://wormbase.org/tools/genome/gbrowse/c_elegans_PRJNA13758/"><i>Caenorhabditis elegans</i></a>, 
                     <a href="https://wormbase.org/tools/genome/gbrowse/c_briggsae_PRJNA10731/"><i>C. briggsae</i></a>, 
                     <a href="https://wormbase.org/tools/genome/gbrowse/c_remanei_PRJNA53967/"><i>C. remanei</i></a>, 
                     <a href="https://wormbase.org/tools/genome/gbrowse/b_malayi_PRJNA10729/"><i>Brugia malayi</i></a>
                     <br>
                     Phytozome: <a href="http://www.phytozome.net/"><i>Chlamydomonas reinhardtii</i></a><br>
                     Flybase: <a href="http://flybase.bio.indiana.edu/cgi-bin/gbrowse/dmel"><i>Drosphila melanogaster</i></a><br>
                     Genboree Browser: <a href="http://www.genboree.org/index.html"><i>Tribolium castaneum</i></a>
                     <div class="csc-default">
                        <div class="divider">
                           <hr>
                        </div>
                     </div>
                  </div>
               </main>
	</body>
</html>
