/*
 * Running this experiment might take a lot of memory if the size of trace
 * file is big (in terms of number of lines/jobs).
 * If you encounter "out of memory" exception, you need to increase JVM heap
 * size using 'java -Xmx' option.
 * For example set the heap size to 300MB:
 * In Unix/Linux:
 *      java -Xmx300000000 -classpath $GRIDSIM/jars/gridsim.jar:. TraceEx02
 * In Windows:
 *      java -Xmx300000000 -classpath %GRIDSIM%\jars\gridsim.jar;. TraceEx02
 *
 * where $GRIDSIM or %GRIDSIM% is the location of the gridsimtoolkit package.
 *
 */
import java.util.*;
import gridsim.*;
import gridsim.util.*;

public class ExampleWorkload {

    public static void main(String[] args) {
        try {
            // number of grid user entities + any Workload entities.
            int num_user = 1;
            Calendar calendar = Calendar.getInstance();
            boolean trace_flag = true;     // mean trace GridSim events

            // Initialize the GridSim package
            System.out.println("Initializing GridSim package");
            GridSim.init(num_user, calendar, trace_flag);

            //////////////////////////////////////////////////////
            /////// Creating resources
            int rating = 100;       // rating of each PE in MIPS
            int totalPE = 12;        // total number of PEs for each Machine
            int totalMachine = 48;   // total number of Machines
            int i = 0;

            String resName = "Res_0";
            CenapadAllocPolicy allocPolicy = new CenapadAllocPolicy(resName, "allocPolicy");
            //TestSpaceShared allocPolicy = new TestSpaceShared(resName, "allocPolicy");
            createGridResource(resName, rating, totalMachine, totalPE, allocPolicy);

            //////////////////////////////////////////////////////
            /////// Creating Workload
            //String tracefile = "workload_mini.jobs"; // custom trace file format
            String tracefile = args[0]; // custom trace file format
            Workload workload
                    = new Workload("Load_0", tracefile, resName, rating);

            // tells the Workload entity what to look for.
            // parameters: maxField, jobNum, submitTime, runTime, numPE
            workload.setField(4, 1, 2, 3, 4);
            workload.setComment("#");     // set "#" as a comment

            //////////////////////////////////////////////////////
            /////// Starts the simulation
            GridSim.startGridSimulation();

            //////////////////////////////////////////////////////
            /////// Print queue times
            GridletList glList = new GridletList();
            for (Gridlet gl : workload.getGridletList()) {
                glList.add(gl);
            }
            printGridletList(glList);
            //workload.printGridletList(false);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void printGridletList(GridletList list) {
        int size = list.size();
        Gridlet gridlet;

        String div = ",";
        //System.out.println();
        //System.out.println("========== OUTPUT ==========");
        System.out.println("Gridlet ID" + div + "SubmissionTime" + div
                + "QueueTime" + div + "RunTime"+div+"State");

        for (int i = 0; i < size; i++) {
            gridlet = (Gridlet) list.get(i);
            System.out.print(gridlet.getGridletID());
            System.out.print(div + gridlet.getSubmissionTime());
            
            double queueTime = gridlet.getWaitingTime();
            System.out.print(div + queueTime);
            
            double runTime = gridlet.getWallClockTime();
            System.out.print(div + runTime);
            
            System.out.println(div + gridlet.getGridletStatusString());
        }
    }

    /**
     * Creates one Grid resource. A Grid resource contains one or more Machines.
     * Similarly, a Machine contains one or more PEs (Processing Elements or
     * CPUs).
     *
     * @param name a Grid Resource name
     * @param peRating rating of each PE
     * @param totalMachine total number of Machines
     * @param totalPE total number of PEs for each Machine
     */
    private static void createGridResource(String name, int peRating,
            int totalMachine, int totalPE, AllocPolicy allocPolicy) {
        //////////////////////////////////////////
        // Here are the steps needed to create a Grid resource:
        // 1. We need to create an object of MachineList to store one or more
        //    Machines
        MachineList mList = new MachineList();

        int rating = peRating;
        for (int i = 0; i < totalMachine; i++) {
            // 2. Create one Machine with its id, number of PEs and rating
            mList.add(new Machine(i, totalPE, rating));
        }

        //////////////////////////////////////////
        // 3. Create a ResourceCharacteristics object that stores the
        //    properties of a Grid resource: architecture, OS, list of
        //    Machines, allocation policy: time- or space-shared, time zone
        //    and its price (G$/PE time unit).
        String arch = "Sun Ultra";      // system architecture
        String os = "Solaris";          // operating system
        double time_zone = 0.0;         // time zone this resource located
        double cost = 3.0;              // the cost of using this resource

        ResourceCharacteristics resConfig = new ResourceCharacteristics(
                arch, os, mList, ResourceCharacteristics.SPACE_SHARED,
                time_zone, cost);

        //////////////////////////////////////////
        // 4. Finally, we need to create a GridResource object.
        double baud_rate = 10000.0;           // communication speed
        long seed = 11L * 13 * 17 * 19 * 23 + 1;
        double peakLoad = 0.0;       // the resource load during peak hour
        double offPeakLoad = 0.0;    // the resource load during off-peak hr
        double holidayLoad = 0.0;    // the resource load during holiday

        // incorporates weekends so the grid resource is on 7 days a week
        LinkedList Weekends = new LinkedList();
        Weekends.add(new Integer(Calendar.SATURDAY));
        Weekends.add(new Integer(Calendar.SUNDAY));

        // incorporates holidays. However, no holidays are set in this example
        LinkedList Holidays = new LinkedList();
        GridResource gridRes = null;
        try {
            ResourceCalendar resCalendar = new ResourceCalendar(time_zone,
                    peakLoad, offPeakLoad, holidayLoad, Weekends,
                    Holidays, seed);

            gridRes = new GridResource(name, baud_rate, resConfig, resCalendar, allocPolicy);
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("Creates one Grid resource with name = " + name);
    }

} // end class

