package edu.tamu.aser.tide.tests;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.DecimalFormat;

public class DataAnalyze {

	public DataAnalyze() {
		// TODO Auto-generated constructor stub
	}

	private static DecimalFormat df2 = new DecimalFormat(".##");

	public void analyze(String tarfile) throws NumberFormatException, IOException{
		File folder = new File(".");

		File[] listOfFiles = folder.listFiles();

		for (File file : listOfFiles) {
			if (file.isFile()) {
				String filename = file.getName();
				if(filename.startsWith("log_" + tarfile)) //&&!filename.endsWith("-opt")
				{

					//String filename = "data-example";
					int totalinstruction=0;

					long totaldeletetime =0;
					long totaladdtime =0;
					long worstdeletetime =0;
					long worstaddtime =0;

					long totaldeletetime_race =0;
					long totaladdtime_race =0;
					long worstdeletetime_race =0;
					long worstaddtime_race =0;

					long totaldeletetime_dl =0;
					long totaladdtime_dl =0;
					long worstdeletetime_dl =0;
					long worstaddtime_dl =0;

					try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
						String line;
						while ((line = br.readLine()) != null) {
							// process the line.
							String[] strs = line.split(" ");
							//make sure it starts with a number
							if(strs.length>0&&!strs[0].isEmpty()
									&&strs[0].charAt(0)>='0'&&strs[0].charAt(0)<='9')
							{
								totalinstruction += strs.length/6;
								for(int i=0;i<strs.length-2;i=i+6)
								{

									int racedeletetime = Integer.parseInt(strs[i]);
									totaldeletetime_race += racedeletetime;
									if(racedeletetime > worstdeletetime_race)
										worstdeletetime_race = racedeletetime;

									int dldeletetime = Integer.parseInt(strs[i+1]);
									totaldeletetime_dl += dldeletetime;
									if(dldeletetime > worstdeletetime_dl)
										worstdeletetime_dl = dldeletetime;

									int raceaddtime = Integer.parseInt(strs[i+2]);
									totaladdtime_race += raceaddtime;
									if(raceaddtime > worstaddtime_race)
										worstaddtime_race = raceaddtime;

									int dladdtime = Integer.parseInt(strs[i+3]);
									totaladdtime_dl += dladdtime;
									if(dladdtime > worstaddtime_dl)
										worstaddtime_dl = dladdtime;

									int ptadeletetime = Integer.parseInt(strs[i+4]);
									totaldeletetime+=ptadeletetime;
									if(ptadeletetime>worstdeletetime)
										worstdeletetime = ptadeletetime;

									int ptaaddtime = Integer.parseInt(strs[i+5]);
									totaladdtime+=ptaaddtime;
									if(ptaaddtime>worstaddtime)
										worstaddtime = ptaaddtime;
								}
							}
						}
					}

					double averagedeletetime = (double)totaldeletetime/totalinstruction;
					double averageaddtime = (double)totaladdtime/totalinstruction;
					double averagetime = (averagedeletetime+averageaddtime)/2;

					double averagedeletetime_race = (double)totaldeletetime_race/totalinstruction;
					double averageaddtime_race = (double)totaladdtime_race/totalinstruction;
					double averagetime_race = (averagedeletetime_race+averageaddtime_race)/2;

					double averagedeletetime_dl = (double)totaldeletetime_dl/totalinstruction;
					double averageaddtime_dl = (double)totaladdtime_dl/totalinstruction;
					double averagetime_dl = (averagedeletetime_dl+averageaddtime_dl)/2;

					System.out.println();
					System.out.println(" =======================================================================");
					System.out.println("Performance of " + tarfile
							+"\n Points-to analysis: "
							+"\n == Average: "+df2.format(averagetime)+ "(Delete: "+df2.format(averagedeletetime)+ " Add: "+df2.format(averageaddtime)+")"
							+"\n == Worst (Delete: "+df2.format(worstdeletetime)+ " Add: "+df2.format(worstaddtime)+")"

							+"\n Race: "
							+"\n == Average: "+df2.format(averagetime_race)+ "(Delete: "+df2.format(averagedeletetime_race)+ " Add: "+df2.format(averageaddtime_race)+")"
							+"\n == Worst (Delete: "+df2.format(worstdeletetime_race)+ " Add: "+df2.format(worstaddtime_race)+")"

							+"\n Deadlock: "
							+"\n == Average: "+df2.format(averagetime_dl)+ "(Delete: "+df2.format(averagedeletetime_dl)+ " Add: "+df2.format(averageaddtime_dl)+")"
							+"\n == Worst (Delete: "+df2.format(worstdeletetime_dl)+ " Add: "+df2.format(worstaddtime_dl)+")"
							);
					System.out.println(" =======================================================================");

				}
			}
		}
	}

}
