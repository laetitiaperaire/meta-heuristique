package jobshop.solvers;

import java.util.ArrayList;
import java.util.List;

import jobshop.Instance;
import jobshop.Result;
import jobshop.Schedule;
import jobshop.Solver;
import jobshop.encodings.ResourceOrder;
import jobshop.encodings.Task;
import jobshop.solvers.DescentSolver.Block;
import jobshop.solvers.DescentSolver.Swap;
import jobshop.solvers.GreedySolver.Priorite;

public class TabouSolver implements Solver{

	private int [] [] taboo_swaps;
	//id of swap with tasks t1 and t2 = [id_t1] [id_t2]
	//id_t1 = t1.job * numb_task + t1.task
	//id_t2 = t2.job * numb_task + t2.task
	private int numb_tasks;
	
	private final int duree_taboo = 5;
	private final int maxIter = 50;

    @Override
    public Result solve(Instance instance, long deadline) 
    {
    	numb_tasks = instance.numTasks;
    	
    	int max_tab = instance.numJobs * numb_tasks + numb_tasks;
    	taboo_swaps = new int[max_tab][max_tab];
    	
    	for(int i = 0 ; i<max_tab ; i++)
    	{
    		for(int j = 0 ; j<max_tab ; j++)
    		{
    			taboo_swaps[i][j] = 0;
    		}
    	}
    	
    	GreedySolver first_solver = new GreedySolver(Priorite.EST_SPT);
    	//RandomSolver first_solver = new RandomSolver();
    	Result first_soluce = first_solver.solve(instance, deadline);
        
    	//to return
    	Result best_current_soluce = first_soluce;
    	
    	Schedule current_schedule = best_current_soluce.schedule;
    	
    	ResourceOrder current_r_order = new ResourceOrder(current_schedule);
    	int current_makespan = current_schedule.makespan();
    	int current_makespan_taboo = current_makespan;
    	
    	boolean can_continue = true;
    	
    	int iter = 0;
    	
    	while(iter < maxIter && can_continue)
    	{
    		iter++;
    		can_continue = false;
    		
    		//when there is not non taboo solutions
    		boolean better_taboo_found = false;
    		Task first_task_taboo = null;
    		Task second_task_taboo = null;
    		ResourceOrder local_taboo_r_order = current_r_order.copy();
    		
    		//when there is a non taboo solution
    		boolean makespan_swaps_is_not_initialized = true;
    		int current_makespan_swaps = -1;
    		boolean valid_swap_found = false;
    		Task first_task_swap = null;
    		Task second_task_swap = null;
    		ResourceOrder local_r_order = current_r_order.copy();
    		
    		//to cover all neighbors
    		List<Block> all_blocks = DescentSolver.blocksOfCriticalPath(current_r_order);
        	int numb_blocks = all_blocks.size();
        	
        	for(int b = 0 ; b < numb_blocks ; b++)
        	{        		
        		List<Swap> all_current_swaps = DescentSolver.neighbors(all_blocks.get(b));
        		int numb_swaps = all_current_swaps.size();
        		
        		for(int s = 0 ; s < numb_swaps ; s++)
        		{
        			//for this neighbor
        			ResourceOrder new_r_order = current_r_order.copy();
        			
        			Swap current_swap = all_current_swaps.get(s);
        			
        			Task first_task = new_r_order.tasksByMachine[current_swap.machine][current_swap.t1];
        			Task second_task = new_r_order.tasksByMachine[current_swap.machine][current_swap.t2];
        			
        			boolean is_taboo = check_is_taboo(iter, first_task, second_task);
        			
        			all_current_swaps.get(s).applyOn(new_r_order);
        			
        			Schedule new_schedule = new_r_order.toSchedule();
        			
        			//checking and choices
        			if(new_schedule != null)
        			{
        				if(new_schedule.isValid())
        				{
        					int new_makespan = new_schedule.makespan();
        					
        					if(is_taboo)
                			{
        						//current_makespan_taboo already initialized with last makespan
        						//for each better neighbor (valid and taboo)
        						if(new_makespan < current_makespan_taboo)
            					{
            						local_taboo_r_order = new_r_order;
            						current_makespan_taboo = new_makespan;
            						better_taboo_found = true;
            						first_task_taboo = first_task;
            						second_task_taboo = second_task;
            					}
                			}
                			else
                			{
                				//made only for the first neighbor (valid and non-taboo)
                				if(makespan_swaps_is_not_initialized)
                				{
                					current_makespan_swaps = new_makespan;
                					makespan_swaps_is_not_initialized = false;
                				}
                				
                				//for each better neighbor (valid and non-taboo)
                				if(new_makespan <= current_makespan_swaps)
            					{
            						local_r_order = new_r_order;
            						current_makespan_swaps = new_makespan;
            						valid_swap_found = true;
            						first_task_swap = first_task;
            						second_task_swap = second_task;
            					}
                			}

        				}
        			}//end checking
        			
        			
        		}//end one neighbor
        	}//end all neighbors
        	
        	can_continue = valid_swap_found || better_taboo_found;
        	
        	if(can_continue)
        	{
        		if(valid_swap_found)
        		{
        			current_r_order = local_r_order;
        			current_makespan = current_makespan_swaps;
        			maj_taboo_swaps(iter, first_task_swap, second_task_swap);
        		}
        		else
        		{
        			current_r_order = local_taboo_r_order;
        			current_makespan = current_makespan_taboo;
        			maj_taboo_swaps(iter, first_task_taboo, second_task_taboo);
        		}
        	}
        	
        	current_makespan_taboo = current_makespan;
        	
    	}//end while
    	
    	best_current_soluce = new Result(best_current_soluce.instance, current_r_order.toSchedule(), best_current_soluce.cause);
    	
    	return best_current_soluce;
    }
    
    //id of swap with tasks t1 and t2 = [id_t1] [id_t2]
  	//id_t1 = t1.job * numb_task + t1.task
  	//id_t2 = t2.job * numb_task + t2.task
    private boolean check_is_taboo(int current_iter, Task t1, Task t2)
    {
    	int id_t1 = t1.job * numb_tasks + t1.task;
      	int id_t2 = t2.job * numb_tasks + t2.task;
      	
    	return taboo_swaps[id_t1][id_t2] > current_iter;
    }
    
    private void maj_taboo_swaps(int current_iter, Task t1, Task t2)
    {
    	int id_t1 = t1.job * numb_tasks + t1.task;
      	int id_t2 = t2.job * numb_tasks + t2.task;
      	
      	//be careful : we avoid the inverse swap to don't come back immediately!!
    	taboo_swaps[id_t2][id_t1] = current_iter + duree_taboo;
    }

}
