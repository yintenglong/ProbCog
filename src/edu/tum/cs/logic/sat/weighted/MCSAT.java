/*
 * Created on Aug 7, 2009
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package edu.tum.cs.logic.sat.weighted;

import java.util.Map;
import java.util.Random;
import java.util.Vector;
import java.util.Map.Entry;

import edu.tum.cs.inference.IParameterHandler;
import edu.tum.cs.inference.ParameterHandler;
import edu.tum.cs.logic.GroundAtom;
import edu.tum.cs.logic.PossibleWorld;
import edu.tum.cs.logic.WorldVariables;
import edu.tum.cs.logic.sat.SampleSAT;
import edu.tum.cs.srl.Database;

public class MCSAT implements IParameterHandler {

	protected WeightedClausalKB kb;
	protected WorldVariables vars;
	protected Database db; 
	protected Random rand;
	protected SampledDistribution dist;
	protected boolean verbose = false, debug = false;
	protected int infoInterval = 100;
	protected ParameterHandler paramHandler;
	protected SampleSAT sat;
	
	public MCSAT(WeightedClausalKB kb, WorldVariables vars, Database db) throws Exception {		
		this.kb = kb;
		this.vars = vars;
		this.db = db;
		this.rand = new Random();
		this.dist = new SampledDistribution(vars);
		this.paramHandler = new ParameterHandler(this);
		PossibleWorld state = new PossibleWorld(vars);
		sat = new SampleSAT(state, vars, db.getEntries());				
		paramHandler.addSubhandler(sat.getParameterHandler());
	}
	
	public WeightedClausalKB getKB() {
		return kb;
	}
	
	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
	}
	
	public void setDebugMode(boolean active) {
		this.debug = active;
	}
	
	public void setInfoInterval(int interval) {
		this.infoInterval = interval;
	}

	public void run(int steps) throws Exception {
		if(debug) {
			System.out.println("\nMC-SAT constraints:");
			for(WeightedClause wc : kb)
				System.out.println("  " + wc);
			System.out.println();
		}
		verbose = verbose || debug;
		if(verbose) 
			System.out.printf("%s sampling...\n", this.getAlgorithmName());		
		
		// find initial state satisfying all hard constraints
		Vector<WeightedClause> M = new Vector<WeightedClause>();
		for(Entry<WeightedFormula, Vector<WeightedClause>> e : kb.getFormulasAndClauses()) {
			WeightedFormula wf = e.getKey();
			if(wf.isHard) {
				M.addAll(e.getValue());
			}
		}		
		sat.setDebugMode(debug);
		sat.initConstraints(M);
		sat.run();
		
		// actual MC-SAT sampling
		for(int i = 0; i < steps; i++) {
			M.clear();
			for(Entry<WeightedFormula, Vector<WeightedClause>> e : kb.getFormulasAndClauses()) {
				WeightedFormula wf = e.getKey();
				if(wf.formula.isTrue(sat.getState())){
					boolean satisfy = wf.isHard || rand.nextDouble() * Math.exp(wf.weight) > 1.0;
					if(satisfy)
						M.addAll(e.getValue());
				}
			}
			if(verbose || (i+1) % infoInterval == 0) {
				System.out.printf("MC-SAT step %d: %d constraints to be satisfied\n", i+1, M.size());
				if(debug) {
					for(WeightedClause wc : M)
						System.out.println("    " + wc);
				}
			}
			sat.initConstraints(M);
			sat.run();
			
			if(false) {
				sat.getState().print();
			}
			
			dist.addSample(sat.getState(), 1.0);
		}
		dist.normalize();
	}
	
	public void setP(double p) {
		sat.setPSampleSAT(p);
	}
	
	public double getResult(GroundAtom ga) {
		return dist.getResult(ga.index);
	}
	
	public static class SampledDistribution{
		public double[] sums;
		public double Z;
		
		public SampledDistribution(WorldVariables vars){
			this.Z = 0.0;
			this.sums = new double[vars.size()];
		}
		
		public void addSample(PossibleWorld w, double weight){
			for(GroundAtom ga : w.getVariables()){
				if(w.isTrue(ga)){
					sums[ga.index] += weight;
				}
			}
			Z += weight;
		}
		
		public void normalize(){
			for(int i = 0; i < sums.length; i++){
				sums[i] /= Z;
			}
			Z = 1.0;
		}
		
		public double getResult(int indx){
			return sums[indx];
		}
	}

	public ParameterHandler getParameterHandler() {
		return paramHandler;
	}

	public void handleParams(Map<String, String> params) throws Exception {
		paramHandler.handle(params);		
	}
	
	public String getAlgorithmName() {
		return String.format("%s[%s]", this.getClass().getSimpleName(), sat.getAlgorithmName());
	}
}
