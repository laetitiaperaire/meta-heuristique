package jobshop.solvers;

import jobshop.Instance;
import jobshop.Result;
import jobshop.Schedule;
import jobshop.Solver;
import jobshop.encodings.ResourceOrder;
import jobshop.encodings.Task;
import jobshop.solvers.GreedySolver.Priorite;

import java.util.ArrayList;
import java.util.List;

public class DescentSolver implements Solver {

    /** A block represents a subsequence of the critical path such that all tasks 
     * in it execute on the same machine.
     * This class identifies a block in a ResourceOrder representation.
     *
     * Consider the solution in ResourceOrder representation
     * machine 0 : (0,1) (1,2) (2,2)
     * machine 1 : (0,2) (2,1) (1,1)
     * machine 2 : ...
     *
     * The block with : machine = 1, firstTask= 0 and lastTask = 1
     * Represent the task sequence : [(0,2) (2,1)]
     *
     * */
    static class Block {
        /** machine on which the block is identified */
        final int machine;
        /** index of the first task of the block */
        final int firstTask;
        /** index of the last task of the block */
        final int lastTask;

        Block(int machine, int firstTask, int lastTask) {
            this.machine = machine;
            this.firstTask = firstTask;
            this.lastTask = lastTask;
        }
        
        public String toString()
        {
        	return "[m=" + machine + ", [" + firstTask + ", " + lastTask + "]]";
        }
    }

    /**
     * Represents a swap of two tasks on the same machine in a ResourceOrder encoding.
     *
     * Consider the solution in ResourceOrder representation
     * machine 0 : (0,1) (1,2) (2,2)
     * machine 1 : (0,2) (2,1) (1,1)
     * machine 2 : ...
     *
     * The swam with : machine = 1, t1= 0 and t2 = 1
     * Represent inversion of the two tasks : (0,2) and (2,1)
     * Applying this swap on the above resource order should result in the following one :
     * machine 0 : (0,1) (1,2) (2,2)
     * machine 1 : (2,1) (0,2) (1,1)
     * machine 2 : ...
     */
    static class Swap {
        // machine on which to perform the swap
        final int machine;
        // index of one task to be swapped
        final int t1;
        // index of the other task to be swapped
        final int t2;

        Swap(int machine, int t1, int t2) {
            this.machine = machine;
            this.t1 = t1;
            this.t2 = t2;
        }

        /** Apply this swap on the given resource order, transforming it into a new solution. */
        public void applyOn(ResourceOrder order) 
        {
        	
        	Task first_Task = order.tasksByMachine[machine][t1];
        	Task second_Task = order.tasksByMachine[machine][t2];
        	
        	order.tasksByMachine[machine][t1] = second_Task;
        	order.tasksByMachine[machine][t2] = first_Task;
        }
    }


    @Override
    public Result solve(Instance instance, long deadline) 
    {
    	GreedySolver first_solver = new GreedySolver(Priorite.EST_SPT);
    	//RandomSolver first_solver = new RandomSolver();
    	Result first_soluce = first_solver.solve(instance, deadline);
        
    	//to return
    	Result best_current_soluce = first_soluce;
    	
    	Schedule current_schedule = best_current_soluce.schedule;
    	
    	//used in each loop of for*for
    	ResourceOrder current_r_order = new ResourceOrder(current_schedule);
    	int current_makespan = current_schedule.makespan();
    	
    	boolean can_find_better = true;
    	
    	while(can_find_better)
    	{
    		can_find_better = false;
    		
    		List<Block> all_blocks = blocksOfCriticalPath(current_r_order);
        	int numb_blocks = all_blocks.size();
        	
        	ResourceOrder local_r_order = current_r_order;
        	
        	//System.out.print("\n" + all_blocks + "\n");
        	
        	for(int b = 0 ; b < numb_blocks ; b++)
        	{
        		//System.out.println("\n  block [ ");
        		
        		List<Swap> all_current_swaps = neighbors(all_blocks.get(b));
        		int numb_swaps = all_current_swaps.size();
        		
        		for(int s = 0 ; s < numb_swaps ; s++)
        		{
        			ResourceOrder new_r_order = current_r_order.copy();
        			all_current_swaps.get(s).applyOn(new_r_order);
        			
        			Schedule new_schedule = new_r_order.toSchedule();
        			
        			if(new_schedule != null)
        			{
        				if(new_schedule.isValid())
        				{
        					int new_makespan = new_schedule.makespan();
        					
        					if(new_makespan < current_makespan)
        					{
        						local_r_order = new_r_order;
        						current_makespan = new_makespan;
        						can_find_better = true;
        					}
        				}
        			}
        		}
        	}
        	
        	current_r_order = local_r_order;
    	}
    	
    	best_current_soluce = new Result(best_current_soluce.instance, current_r_order.toSchedule(), best_current_soluce.cause);
    	
    	return best_current_soluce;
    }

    
   
    
    
    // Returns a list of all blocks of the critical path.
    static public List<Block> blocksOfCriticalPath(ResourceOrder order) 
    {
    	Schedule soluce = order.toSchedule();
    	
    	List<Task> path = soluce.criticalPath();
    	
    	List<Block> result = new ArrayList<Block>();
    	
    	boolean is_in_block = false;
    	int machine_current_block = -1;
    	int first_task_current_block = -1;
    	int last_task_current_block = -1;
    	
    	int last_machine = -1;
    	//int last_end_time = -1;
    	
    	int [] current_state = new int[soluce.pb.numMachines];
    	
    	for(int i = 0 ; i < soluce.pb.numMachines ; i++)
    	{
    		current_state[i] = -1;
    	}
    	
    	for(int t = 0 ; t < path.size() ; t++)
    	{
    		Task current_task = path.get(t);
    		int current_machine = soluce.pb.machine(current_task);
    		current_state[current_machine] = get_index_task(current_machine, current_task, order);
    		//int current_start_time = soluce.startTime(current_task);
    		//int current_end_time = soluce.endTime(current_task);
    		
    		if(current_machine == last_machine) // && last_end_time == current_start_time-1
    		{
    			if(!is_in_block)
    			{
    				machine_current_block = current_machine;
    				//first_task_current_block = t-1; //index in the path
    				first_task_current_block = current_state[current_machine]-1; //index in the machine
    				is_in_block = true;
    			}
    		}
    		else
    		{
    			if(is_in_block)
    			{
    				//last_task_current_block = t-1; //index in the path
    				last_task_current_block = current_state[last_machine]; //index in the machine
    				result.add(new Block(machine_current_block, first_task_current_block, last_task_current_block));
    				
    				is_in_block = false;
    			}
    		}
    		
    		last_machine = current_machine;
    		//last_end_time = current_end_time;
    	}
    	
    	if(is_in_block)
    	{
    		//last_task_current_block = path.size()-1; //index in the path
    		last_task_current_block = current_state[last_machine]; //index in the machine
			result.add(new Block(machine_current_block, first_task_current_block, last_task_current_block));
			
			is_in_block = false;
    	}
    	
        return result;
    }
    
    
    static private int get_index_task(int machine, Task task, ResourceOrder order)
    {
    	int result = -1;
    	
    	Task [] array = order.tasksByMachine[machine];
    	
    	int size = array.length;
    	int t = 0 ;
    	boolean not_found = true;
    	while(not_found && t<size)
    	{
    		if(array[t].job == task.job && array[t].task == task.task)
    		{
    			not_found = false;
    			result = t;
    		}
    		t++;
    	}
    	
    	return result;
    }
    
  
    

    /** For a given block, return the possible swaps for the Nowicki and Smutnicki neighborhood */
    static public List<Swap> neighbors(Block block) 
    {
    	int machine = block.machine;
    	int first = block.firstTask;
    	int last = block.lastTask;
    	
    	List<Swap> result = new ArrayList<Swap>();
    	
    	if(last == first+1)
    	{
    		result.add(new Swap(machine, last, first));
    	}
    	else
    	{
    		result.add(new Swap(machine, first, first+1));
    		result.add(new Swap(machine, last-1, last));    		
    	}
    	
    	/*
    	 * trop de swap => on a besoin seulement des extrémités
    	for(int s = first; s < last ; s++)
    	{
    		result.add(new Swap(machine, s, s+1));
    	}
    	*/
    	
        return result;
    }

}
