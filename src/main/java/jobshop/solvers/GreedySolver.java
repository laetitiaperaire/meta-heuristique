package jobshop.solvers;

import java.util.ArrayList;

import jobshop.Instance;
import jobshop.Result;
import jobshop.Solver;
import jobshop.encodings.*;


public class GreedySolver implements Solver {
	
	//recherche gloutonne basée sur ResourceOrder
	// 4 priorités au choix: SPT , LRPT , EST_SPT et EST_LRPT
	
	/*
	 * donne priorité à la tâche...
	 	• SPT (Shortest Processing Time) : la plus courte ; 
		• LPT (Longest Processing Time) : la plus longue ; 
		• SRPT (Shortest Remaining Processing Time) : appartenant au job ayant la plus petite durée restante; 
		• LRPT (Longest Remaining Processing Time) : appartenant au job ayant la plus grande durée;
	 */
	
	private Priorite prio;
	
	// indicate for each task that have been scheduled, its start time
	private int [][] startTimes;
	
	// for each machine, earliest time at which the machine can be used
    private int[] releaseTimeOfMachine;
	
	public enum Priorite
	{
		SPT,
		//LPT,
		LRPT,
		//SRPT,
		EST_SPT,
		//EST_LPT,
		EST_LRPT,
		//EST_SRPT,
	}
	
	
	public GreedySolver(Priorite prio)
	{
		this.prio = prio;
	}

	public Result solve(Instance instance, long deadline) 
	{
		ResourceOrder soluce = new ResourceOrder(instance);
		
		//pour les calculs de EST
		startTimes = new int [instance.numJobs][instance.numTasks];
		releaseTimeOfMachine = new int[instance.numMachines];
		
		//liste des taches réalisables = soluce.nextFreeSlot
		ArrayList<Task> realisable = new ArrayList<Task>();
		//init
		for(int j = 0; j< soluce.instance.numJobs ; j++)
		{
			realisable.add(new Task(j,0));
		}
		
		//number of task ordered on each machine
		for(int m = 0; m< soluce.instance.numMachines ; m++)
		{
			soluce.nextFreeSlot[m] = 0;
		}
		
		while(!realisable.isEmpty())
		{
			Task task_prio = new Task(0, 0);
			
			if(prio == Priorite.SPT)
			{
				task_prio = choose_task_SPT(realisable, soluce.instance);
			}
			else if(prio == Priorite.LRPT)
			{
				task_prio = choose_task_LRPT(realisable, soluce.instance);
			}
			else if(prio == Priorite.EST_SPT || prio == Priorite.EST_LRPT)
			{
				task_prio = choose_task_EST(realisable, soluce.instance);
			}
			
			int nb_machine = soluce.instance.machine(task_prio);
			int range_next_task = soluce.nextFreeSlot[nb_machine];
			
			soluce.tasksByMachine[nb_machine][range_next_task] = task_prio;
			
			soluce.nextFreeSlot[nb_machine]++;
			
			realisable.remove(task_prio);
			
			if((task_prio.task + 1) != soluce.instance.numTasks)
			{
				realisable.add(new Task(task_prio.job, task_prio.task+1));
			}
		}
		
		return new Result(instance, soluce.toSchedule(), Result.ExitCause.Blocked);
	}
	
	private Task choose_task_EST(ArrayList<Task> list, Instance data)
	{
		Task result = new Task(0,0);
		ArrayList<Task> sub_list = new ArrayList<Task>();
		
		int earliest_begin = earliest_beginning(list.get(0), data);
		sub_list.add(list.get(0));
		
		for(int t = 1 ; t < list.size() ; t++)
		{
			int current_task_est = earliest_beginning(list.get(t), data);
			
			if(current_task_est == earliest_begin)
			{
				sub_list.add(list.get(t));
			}
			else if(current_task_est < earliest_begin)
			{
				sub_list.clear();
				sub_list.add(list.get(t));
				earliest_begin = current_task_est;
			}
		}
		
		if(sub_list.size() == 1)
		{
			result = sub_list.get(0);
		}
		else
		{
			if(prio == Priorite.EST_SPT)
			{
				result = choose_task_SPT(sub_list, data);
			}
			else if(prio == Priorite.EST_LRPT)
			{
				result = choose_task_LRPT(sub_list, data);
			}
			
		}
		
		startTimes[result.job][result.task] = earliest_begin;
		releaseTimeOfMachine[data.machine(result)] = earliest_begin + data.duration(result);
		
		return result;
	}
	
	private int earliest_beginning(Task t, Instance data)
	{
        int est = t.task == 0 ? 0 : startTimes[t.job][t.task-1] + data.duration(t.job, t.task-1);
        est = Math.max(est, releaseTimeOfMachine[data.machine(t)]);
        
		return est;
	}
	
	private Task choose_task_SPT(ArrayList<Task> list, Instance data)
	{
		//priorité à la tache la plus courte
		Task result = list.get(0);
		int shortest_duration = data.duration(result);
		
		for(int i = 1 ; i<list.size() ; i++)
		{
			Task current_task = list.get(i);
			int current_duration = data.duration(current_task);
			
			if(current_duration < shortest_duration)
			{
				result = current_task;
				shortest_duration = current_duration;
			}
		}
		
		return result;
	}
	
	private Task choose_task_LRPT(ArrayList<Task> list, Instance data)
	{
		//priorité à la tache appartenant au job ayant la plus grande durée
		
		Task result = list.get(0);
		int longuest_duration_in_job = data.duration(result);
		
		for(int i_task=result.task ; i_task < data.numTasks ; i_task++)
		{
			longuest_duration_in_job += data.duration(result.job, i_task);
		}
		
		for(int i = 1 ; i<list.size() ; i++)
		{
			Task current_task = list.get(i);
			int current_duration = data.duration(current_task);
			
			for(int i_task=current_task.task ; i_task < data.numTasks ; i_task++)
			{
				current_duration += data.duration(current_task.job, i_task);
			}
			
			if(current_duration > longuest_duration_in_job)
			{
				result = current_task;
				longuest_duration_in_job = current_duration;
			}
		}
		
		return result;
	}
	
	public String getPriorite()
	{
		return prio.toString();
	}

}
