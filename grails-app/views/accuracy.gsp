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
                        <h1 class="csc-firstHeader">Accuracy of AUGUSTUS</h1>
                     </div>
                  </div>
                  <div id="c261665" class="csc-default">
                     <div class="csc-default">
                        <div class="divider">
                           <hr>
                        </div>
                     </div>
                     <p>
                        AUGUSTUS is used in many genome annotation projects. Below are some accuracy values in comparison to other programs.
                        As accuracy measure we use <i>sensitivity</i> (Sn) and <i>specificity</i> (Sp). For a
                        feature (coding base, exon, transcript, gene) the sensitivity is defined as the
                        number of correctly predicted features divided by the number of
                        annotated features. The specificity is the number of correctly
                        predicted features divided by the number of predicted features. A
                        predicted exon is considered correct if both splice sites are at
                        the annotated position of an exon. A predicted transcript is considered
                        correct if all exons are correctly predicted and no additional exons not in the annotation.
                        A predicted gene is considered correct if any of its transcripts are correct, i.e. if
                        at least one isoform of the gene is exactly as annotated in the reference annotation.
                     </p>
                     <h2>Accuracy results from the <a href="https://www.gencodegenes.org/pages/rgasp/">rGASP Assessment</a> (round 2) using RNA-Seq</h2>
                     <br>
                     <p>The complete accuracy statistics is available on a page from the <a href="https://compgen.bio.ub.edu/tiki-index.php?page=RGASP+Summary+of+Evaluation+Results#RGASP:_ROUND_2">Computational Genomics Lab, Barcelona</a>. <i>Below pictures are loaded only after confirming the site authenticity. Click on broken image to confirm and enlarge!</i></p>
                     <table>
                        <tr>
                           <td align="center"><b>human</b> coding exon level</td>
                           <td align="center"><b>human</b> coding transcript level</td>
                        </tr>
                        <tr>
                           <td align="center"><a href="https://compgen.bio.ub.edu/intranet/datasets/RGASP.r2/b/boxplot_human_SNCDS_SPCDS_chrALL_ALL.png"><img src="https://compgen.bio.ub.edu/intranet/datasets/RGASP.r2/b/boxplot_human_SNCDS_SPCDS_chrALL_ALL.png" alt="human CDS level" width="140%"></a></td>
                           <td align="center"><a href="https://compgen.bio.ub.edu/intranet/datasets/RGASP.r2/b/boxplot_human_C.SNt_C.SPt_chrALL_ALL.png"><img src="https://compgen.bio.ub.edu/intranet/datasets/RGASP.r2/b/boxplot_human_C.SNt_C.SPt_chrALL_ALL.png" alt="human CDS level" width="140%"></a></td>
                        </tr>
                     </table>
                     <table>
                        <tr>
                           <td align="center"><b>fly</b> coding exon level</td>
                           <td align="center"><b>fly</b> gene level</td>
                        </tr>
                        <tr>
                           <td align="center"><a href="https://compgen.bio.ub.edu/intranet/datasets/RGASP.r2/b/boxplot_fly_SNCDS_SPCDS_chrALL_ALL.png">
                              <img src="https://compgen.bio.ub.edu/intranet/datasets/RGASP.r2/b/boxplot_fly_SNCDS_SPCDS_chrALL_ALL.png" alt="fly CDS level"></a></td>
                           <td align="center"><a href="https://compgen.bio.ub.edu/intranet/datasets/RGASP.r2/b/boxplot_fly_C.SNg_C.SPg_chrALL_ALL.png"><img src="https://compgen.bio.ub.edu/intranet/datasets/RGASP.r2/b/boxplot_fly_C.SNg_C.SPg_chrALL_ALL.png" alt="fly CDS level"></a></td>
                        </tr>
                     </table>
                     <table>
                        <tr>
                           <td align="center"><b>worm</b> coding exon level</td>
                        </tr>
                        <tr>
                           <td align="center"><a href="https://compgen.bio.ub.edu/intranet/datasets/RGASP.r2/b/boxplot_worm_SNCDS_SPCDS_chrALL_ALL.png"><img src="https://compgen.bio.ub.edu/intranet/datasets/RGASP.r2/b/boxplot_worm_SNCDS_SPCDS_chrALL_ALL.png" alt="worm CDS level"></a></td>
                        </tr>
                     </table>
                     Above accuracy plots are from <a href="https://compgen.bio.ub.edu/tiki-index.php?page=RGASP+Summary+of+Evaluation+Results#RGASP:_ROUND_2">Josep Abril, Computational Genomics Lab</a>.
                     Our AUGUSTUS predictions are labelled Mar.*. The worst performing prediction of AUGUSTUS (there are 3 sets in human, and worm each, and 2 sets in fly) are <b><i>ab initio</i> predictions</b> and do not use any RNA-Seq at all.
                     <a href="https://compgen.bio.ub.edu/tiki-index.php?page=RGASP+Summary+of+Evaluation+Results#RGASP:_ROUND_2">Other participant codes are here</a>.<br><br>
                     <h2>Accuracy results from the <a href="https://wiki.wormbase.org/index.php/NGASP">nGASP Assessment</a></h2>
                     <br>
                     <table border width="630">
                        <caption>Accuracy results from recent nGASP assessment on <i>C. elegans</i>: transcript-based</caption>
                        <tr>
                           <td>program</td>
                           <td colspan="2">base</td>
                           <td colspan="2">exon</td>
                           <td colspan="2">transcript</td>
                           <td colspan="2">gene</td>
                        <tr>
                           <td>       </td>
                           <td>Sn</td>
                           <td>Sp</td>
                           <td>Sn</td>
                           <td>Sp</td>
                           <td>Sn</td>
                           <td>Sp</td>
                           <td>Sn</td>
                           <td>Sp</td>
                        </tr>
                        <tr>
                           <td>AUGUSTUS</td>
                           <td>99.0</td>
                           <td>90.5</td>
                           <td>92.5</td>
                           <td>80.2</td>
                           <td>68.3</td>
                           <td>47.1</td>
                           <td>80.1</td>
                           <td>51.8</td>
                        </tr>
                        <tr>
                           <td>Fgenesh++</td>
                           <td>97.6</td>
                           <td>89.7</td>
                           <td>90.4</td>
                           <td>80.9</td>
                           <td>65.5</td>
                           <td>53.4</td>
                           <td>78.3</td>
                           <td>54.2</td>
                        </tr>
                        <tr>
                           <td>MGENE</td>
                           <td>98.7</td>
                           <td>91.9</td>
                           <td>91.0</td>
                           <td>80.6</td>
                           <td>57.7</td>
                           <td>48.0</td>
                           <td>70.6</td>
                           <td>51.1</td>
                        </tr>
                        <tr>
                           <td>EUGENE</td>
                           <td>98.5</td>
                           <td>85.1</td>
                           <td>92.1</td>
                           <td>70.3</td>
                           <td>60.8</td>
                           <td>31.5</td>
                           <td>68.8</td>
                           <td>36.1</td>
                        </tr>
                        <tr>
                           <td>ExonHunter</td>
                           <td>93.7</td>
                           <td>92.0</td>
                           <td>81.2</td>
                           <td>76.9</td>
                           <td>37.2</td>
                           <td>39.7</td>
                           <td>45.6</td>
                           <td>40.5</td>
                        </tr>
                        <tr>
                           <td>Gramene</td>
                           <td>98.2</td>
                           <td>95.4</td>
                           <td>88.5</td>
                           <td>71.8</td>
                           <td>41.7</td>
                           <td>19.6</td>
                           <td>48.7</td>
                           <td>37.2</td>
                        </tr>
                        <tr>
                           <td>MAKER</td>
                           <td>92.9</td>
                           <td>88.5</td>
                           <td>80.7</td>
                           <td>66.3</td>
                           <td>41.3</td>
                           <td>19.6</td>
                           <td>50.7</td>
                           <td>47.6</td>
                        </tr>
                     </table>
                     Above accuracy values are taken from <a href="https://bmcbioinformatics.biomedcentral.com/articles/10.1186/1471-2105-9-549">Coghlan et al. (2008):
                     <i>nGASP: the nematode genome annotation assessment project</i></a>.<br><br>
                     <table border width="630">
                        <caption>Accuracy results from recent nGASP assessment on <i>C. elegans</i>: <i>ab initio</i></caption>
                        <tr>
                           <td>program</td>
                           <td colspan="2">base</td>
                           <td colspan="2">exon</td>
                           <td colspan="2">transcript</td>
                           <td colspan="2">gene</td>
                        <tr>
                           <td>       </td>
                           <td>Sn</td>
                           <td>Sp</td>
                           <td>Sn</td>
                           <td>Sp</td>
                           <td>Sn</td>
                           <td>Sp</td>
                           <td>Sn</td>
                           <td>Sp</td>
                        </tr>
                        <tr>
                           <td>AUGUSTUS</td>
                           <td>97.0</td>
                           <td>89.0</td>
                           <td>86.1</td>
                           <td>72.6</td>
                           <td>50.1</td>
                           <td>28.7</td>
                           <td>61.1</td>
                           <td>38.4</td>
                        </tr>
                        <tr>
                           <td>Fgenesh</td>
                           <td>98.2</td>
                           <td>87.1</td>
                           <td>86.4</td>
                           <td>73.6</td>
                           <td>47.1</td>
                           <td>34.6</td>
                           <td>57.8</td>
                           <td>35.4</td>
                        </tr>
                        <tr>
                           <td>GeneMark.hmm</td>
                           <td>98.3</td>
                           <td>83.1</td>
                           <td>83.2</td>
                           <td>65.6</td>
                           <td>37.7</td>
                           <td>24.0</td>
                           <td>46.3</td>
                           <td>24.5</td>
                        </tr>
                        <tr>
                           <td>MGENE</td>
                           <td>97.2</td>
                           <td>91.5</td>
                           <td>84.6</td>
                           <td>78.6</td>
                           <td>44.6</td>
                           <td>40.9</td>
                           <td>54.8</td>
                           <td>42.3</td>
                        </tr>
                        <tr>
                           <td>GeneID</td>
                           <td>93.9</td>
                           <td>88.2</td>
                           <td>77.0</td>
                           <td>68.6</td>
                           <td>36.2</td>
                           <td>22.8</td>
                           <td>44.4</td>
                           <td>25.1</td>
                        </tr>
                        <tr>
                           <td>Agene</td>
                           <td>93.8</td>
                           <td>83.4</td>
                           <td>68.9</td>
                           <td>61.1</td>
                           <td>9.8</td>
                           <td>13.1</td>
                           <td>12.0</td>
                           <td>14.1</td>
                        </tr>
                        <tr>
                           <td>CRAIG</td>
                           <td>95.6</td>
                           <td>90.9</td>
                           <td>80.2</td>
                           <td>78.2</td>
                           <td>35.7</td>
                           <td>36.3</td>
                           <td>43.8</td>
                           <td>37.8</td>
                        </tr>
                        <tr>
                           <td>EUGENE</td>
                           <td>94.0</td>
                           <td>89.5</td>
                           <td>80.3</td>
                           <td>73.0</td>
                           <td>49.1</td>
                           <td>28.8</td>
                           <td>60.2</td>
                           <td>30.2</td>
                        </tr>
                        <tr>
                           <td>ExonHunter</td>
                           <td>95.4</td>
                           <td>86.0</td>
                           <td>72.6</td>
                           <td>62.5</td>
                           <td>15.5</td>
                           <td>18.6</td>
                           <td>19.1</td>
                           <td>19.2</td>
                        </tr>
                        <tr>
                           <td>GlimmerHMM</td>
                           <td>97.6</td>
                           <td>87.6</td>
                           <td>84.4</td>
                           <td>71.4</td>
                           <td>47.3</td>
                           <td>29.3</td>
                           <td>58.0</td>
                           <td>30.6</td>
                        </tr>
                        <tr>
                           <td>SNAP</td>
                           <td>94.0</td>
                           <td>84.5</td>
                           <td>74.6</td>
                           <td>61.3</td>
                           <td>32.6</td>
                           <td>18.6</td>
                           <td>40.0</td>
                           <td>19.1</td>
                        </tr>
                     </table>
                     Above accuracy values are taken from <a href="https://bmcbioinformatics.biomedcentral.com/articles/10.1186/1471-2105-9-549">Coghlan et al. (2008):
                     <i>nGASP: the nematode genome annotation assessment project</i></a>.<br><br>
                     <h2>Accuracy results from the <a href="https://genomebiology.biomedcentral.com/articles/10.1186/gb-2006-7-s1-s2">EGASP Assessment</a></h2>
                     <br>
                     <table border width="630">
                        <caption>Accuracy results on human ENCODE regions (ab initio)</caption>
                        <tr>
                           <td></td>
                           <td>AUGUSTUS</td>
                           <td>GENSCAN</td>
                           <td>GENEID</td>
                           <td>GENEMARK</td>
                           <td>GENEZILLA</td>
                        </tr>
                        <tr>
                           <td>base level sensitivity</td>
                           <td>78.65%</td>
                           <td>84.17%</td>
                           <td>76.77%</td>
                           <td>76.09%</td>
                           <td><b>87.56%</b></td>
                        </tr>
                        <tr>
                           <td>base level specificity</td>
                           <td>75.29%</td>
                           <td>60.60%</td>
                           <td><b>76.48%</b></td>
                           <td>62.94%</td>
                           <td>50.93%</td>
                        </tr>
                        <tr>
                           <td>exon level sensitivity</td>
                           <td>52.39%</td>
                           <td>58.65%</td>
                           <td>53.84%</td>
                           <td>48.15%</td>
                           <td><b>62.08%</b></td>
                        </tr>
                        <tr>
                           <td>exon level specificity</td>
                           <td><b>62.93%</b></td>
                           <td>46.37%</td>
                           <td>61.08%</td>
                           <td>47.25%</td>
                           <td>50.25%</td>
                        </tr>
                        <tr>
                           <td>gene level sensitivity</td>
                           <td><b>24.32%</b></td>
                           <td>15.54%</td>
                           <td>10.47%</td>
                           <td>16.89%</td>
                           <td>19.59%</td>
                        </tr>
                        <tr>
                           <td>gene level specificity</td>
                           <td><b>17.22%</b></td>
                           <td>10.13%</td>
                           <td>8.78%</td>
                           <td>7.91%</td>
                           <td>8.84%</td>
                        </tr>
                     </table>
                     Above accuracy values are taken from <a href="http://genomebiology.com/2006/7/S1/S2">Guig&#243; et al. (2006): <i>EGASP: the human ENCODE Genome Annotation Assessment Project</i></a>.<br><br>
                     <table border width="500">
                        <caption>Accuracy results on fruit fly data set adh222</caption>
                        <tr>
                           <td>long drosophila sequence</td>
                           <td align="center" colspan="3">Program</td>
                        </tr>
                        <tr>
                           <td></td>
                           <td>AUGUSTUS</td>
                           <td>GENEID</td>
                           <td>GENIE</td>
                        </tr>
                        <tr>
                           <td>base level sensitivity (std1)</td>
                           <td>98%</td>
                           <td>96%</td>
                           <td>96%</td>
                        </tr>
                        <tr>
                           <td>base level specificity (std3)</td>
                           <td>93%</td>
                           <td>92%</td>
                           <td>92%</td>
                        </tr>
                        <tr>
                           <td>exon level sensitivity (std1)</td>
                           <td>86%</td>
                           <td>71%</td>
                           <td>70%</td>
                        </tr>
                        <tr>
                           <td>exon level specificity (std3)</td>
                           <td>66%</td>
                           <td>62%</td>
                           <td>57%</td>
                        </tr>
                        <tr>
                           <td>gene level sensitivity (std1)</td>
                           <td>71%</td>
                           <td>47%</td>
                           <td>40%</td>
                        </tr>
                        <tr>
                           <td>gene level specificity (std3)</td>
                           <td>39%</td>
                           <td>33%</td>
                           <td>29%</td>
                        </tr>
                     </table>
                     <br>
                     <table border width="500">
                        <caption>Accuracy results on Arabidopsis data set araset</caption>
                        <tr>
                           <td>multi-gene sequences</td>
                           <td align="center" colspan="3">Program</td>
                        </tr>
                        <tr>
                           <td></td>
                           <td>AUGUSTUS</td>
                        <tr>
                           <td>base level sensitivity</td>
                           <td>97%</td>
                        </tr>
                        <tr>
                           <td>base level specificity</td>
                           <td>72%</td>
                        </tr>
                        <tr>
                           <td>exon level sensitivity</td>
                           <td>89%</td>
                        </tr>
                        <tr>
                           <td>exon level specificity</td>
                           <td>70%</td>
                        </tr>
                        <tr>
                           <td>gene level sensitivity</td>
                           <td>62%</td>
                        </tr>
                        <tr>
                           <td>gene level specificity</td>
                           <td>39%</td>
                        </tr>
                     </table>
                     <p><b>adh222</b> is a single sequence of drosophila melanogaster and
                        2.9Mb long.<br>
                        There are two sets of annotations. The first, smaller set,
                        called std1, was chosen so that the genes in it are likely to be
                        correctly annotated and the second larger set, called std3, was chosen to be
                        as complete as possible.<br>
                        This dataset and the annotation was taken from
                        <a href="http://www.fruitfly.org/GASP1/data/standard.html">here</a>.
                     </p>
                     <p>
                        In the corrected version std1 contains 38 genes with a total of 111
                        exons and
                        std3 contains 222 genes with a total of 909 exons.<br>
                        The genes lie on both strands.
                     </p>
                     <p><b>araset</b> is a set of 74 multi-gene sequences with 168 genes of Arabidopsis thaliana. 
                        The specificity is likely to be underestimated because there are sometimes genes at the boundaries of a sequence that are not annotated.
                     </p>
                     <p>The <a href="${createLink(uri:'/datasets')}">datasets</a> can be downloaded.</p>
                     <div class="csc-default">
                        <div class="divider">
                           <hr>
                        </div>
                     </div>
                  </div>
               </main>
	</body>
</html>
