/*
 * Title:        GridSim Toolkit
 * Description:  GridSim (Grid Simulation) Toolkit for Modeling and Simulation
 *               of Parallel and Distributed Systems such as Clusters and Grids
 * License:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 */
import java.util.Iterator;
import eduni.simjava.Sim_event;
import eduni.simjava.Sim_system;
import gridsim.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SpaceShared class is an allocation policy for GridResource that behaves
 * exactly like First Come First Serve (FCFS). This is a basic and simple
 * scheduler that runs each Gridlet to one Processing Element (PE). If a Gridlet
 * requires more than one PE, then this scheduler only assign this Gridlet to
 * one PE.
 *
 * @author Manzur Murshed and Rajkumar Buyya
 * @author Anthony Sulistio (re-written this class)
 * @author Marcos Dias de Assuncao (has made some methods synchronized)
 * @since GridSim Toolkit 2.2
 * @see gridsim.GridSim
 * @see gridsim.ResourceCharacteristics
 * @invariant $none
 */
class CenapadAllocPolicy extends AllocPolicy {

    private ResGridletList gridletLongQueueList_;     // Queue list
    private ResGridletList gridletMediumQueueList_;     // Queue list
    private ResGridletList gridletInExecList_;    // Execution list
    private ResGridletList gridletPausedList_;    // Pause list
    private double lastUpdateTime_;    // the last time Gridlets updated
    private int[] machineRating_;      // list of machine ratings available
    private int maxPeSize;
    private double pePerMachine;
    private final double minimumSpan = 60 * 60;
    private double lastPrintedTrace = 0;
    private PrintStream res_trace = null;
    private final int PartitionMedium = 0;
    private final int PartitionLong = 1;
    private Set<Integer> mediumPartitionIds;

    /**
     * Allocates a new SpaceShared object
     *
     * @param resourceName the GridResource entity name that will contain this
     * allocation policy
     * @param entityName this object entity name
     * @throws Exception This happens when one of the following scenarios occur:
     * <ul>
     * <li> creating this entity before initializing GridSim package
     * <li> this entity name is <tt>null</tt> or empty
     * <li> this entity has <tt>zero</tt> number of PEs (Processing Elements).
     * <br>
     * No PEs mean the Gridlets can't be processed. A GridResource must contain
     * one or more Machines. A Machine must contain one or more PEs.
     * </ul>
     * @see gridsim.GridSim#init(int, Calendar, boolean, String[], String[],
     * String)
     * @pre resourceName != null
     * @pre entityName != null
     * @post $none
     */
    CenapadAllocPolicy(String resourceName, String entityName) throws Exception {
        super(resourceName, entityName);

        // initialises local data structure
        this.gridletInExecList_ = new ResGridletList();
        this.gridletPausedList_ = new ResGridletList();
        this.gridletLongQueueList_ = new ResGridletList();
        this.gridletMediumQueueList_ = new ResGridletList();
        this.mediumPartitionIds = new HashSet<>();
        this.lastUpdateTime_ = 0.0;
        this.machineRating_ = null;

        File fp = new File("res_trace.csv");
        try {
            res_trace = new PrintStream(fp);
            res_trace.println("timestamp,usedPEs,jobsRunning,JobsInLongQueue,JobsInMediumQueue");
        } catch (FileNotFoundException ex) {
            Logger.getLogger(CenapadAllocPolicy.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Handles internal events that are coming to this entity.
     *
     * @pre $none
     * @post $none
     */
    @Override
    public void body() {
        // Gets the PE's rating for each Machine in the list.
        // Assumed every PE of one Machine has same MIPS rating.
        pePerMachine = resource_.getMachineList().get(0).getNumPE();
        maxPeSize = (int) pePerMachine * resource_.getMachineList().size();

        MachineList list = super.resource_.getMachineList();
        int size = list.size();
        machineRating_ = new int[size];
        for (int i = 0; i < size; i++) {
            machineRating_[i] = super.resource_.getMIPSRatingOfOnePE(i, 0);
        }

        int medium_size = 38;
        for (Machine m : resource_.getMachineList()) {
            if (mediumPartitionIds.size() >= medium_size) {
                break;
            }
            mediumPartitionIds.add(m.getMachineID());
        }

        // a loop that is looking for internal events only
        Sim_event ev = new Sim_event();
        while (Sim_system.running()) {
            super.sim_get_next(ev);

            // if the simulation finishes then exit the loop
            if (ev.get_tag() == GridSimTags.END_OF_SIMULATION
                    || super.isEndSimulation()) {
                break;
            }

            // Internal Event if the event source is this entity
            if (ev.get_src() == super.myId_ && gridletInExecList_.size() > 0) {
                updateGridletProcessing();   // update Gridlets
                checkGridletCompletion();    // check for finished Gridlets
            }
//            System.out.print("Machines in use: ");
//            for (ResGridlet rgl : gridletInExecList_) {
//                if (rgl.getListMachineID() == null) {
//                    System.out.print(rgl.getMachineID() + " ");
//                } else {
//                    for (int m : rgl.getListMachineID()) {
//                        System.out.print(m + " ");
//                    }
//                }
//            }
//            System.out.println();
        }

        // CHECK for ANY INTERNAL EVENTS WAITING TO BE PROCESSED
        while (super.sim_waiting() > 0) {
            System.out.println("waiting event");
            // wait for event and ignore since it is likely to be related to
            // internal event scheduled to update Gridlets processing
            super.sim_get_next(ev);
            System.out.println(super.resName_
                    + ".SpaceShared.body(): ignore internal events");
        }

        res_trace.close();
    }

    /**
     * Schedules a new Gridlet that has been received by the GridResource
     * entity.
     *
     * @param gl a Gridlet object that is going to be executed
     * @param ack an acknowledgement, i.e. <tt>true</tt> if wanted to know
     * whether this operation is success or not, <tt>false</tt>
     * otherwise (don't care)
     * @pre gl != null
     * @post $none
     */
    @Override
    public synchronized void gridletSubmit(Gridlet gl, boolean ack) {
        // update the current Gridlets in exec list up to this point in time
        updateGridletProcessing();

        ResGridlet rgl = new ResGridlet(gl);
        int partition = gl.getClassType();

        boolean success = allocatePEtoGridlet(rgl, partition);
        // if no available PE then put the ResGridlet into a Queue list
        if (!success) {
            rgl.setGridletStatus(Gridlet.QUEUED);
            if (partition == PartitionMedium) {
                gridletMediumQueueList_.add(rgl);
            } else {
                gridletLongQueueList_.add(rgl);
            }
        }

        // sends back an ack if required
        if (ack) {
            super.sendAck(GridSimTags.GRIDLET_SUBMIT_ACK, true,
                    gl.getGridletID(), gl.getUserID()
            );
        }
    }

    /**
     * Finds the status of a specified Gridlet ID.
     *
     * @param gridletId a Gridlet ID
     * @param userId the user or owner's ID of this Gridlet
     * @return the Gridlet status or <tt>-1</tt> if not found
     * @see gridsim.Gridlet
     * @pre gridletId > 0
     * @pre userId > 0
     * @post $none
     */
    @Override
    public synchronized int gridletStatus(int gridletId, int userId) {
        ResGridlet rgl;

        // Find in EXEC List first
        int found = gridletInExecList_.indexOf(gridletId, userId);
        if (found >= 0) {
            // Get the Gridlet from the execution list
            rgl = (ResGridlet) gridletInExecList_.get(found);
            return rgl.getGridletStatus();
        }

        // Find in Paused List
        found = gridletPausedList_.indexOf(gridletId, userId);
        if (found >= 0) {
            // Get the Gridlet from the execution list
            rgl = (ResGridlet) gridletPausedList_.get(found);
            return rgl.getGridletStatus();
        }

        // Find in Queue List
        //found = gridletQueueList_.indexOf(gridletId, userId);
//        if (found >= 0) {
//            // Get the Gridlet from the execution list
//            rgl = (ResGridlet) gridletQueueList_.get(found);
//            return rgl.getGridletStatus();
//        }
        // if not found in all 3 lists then no found
        return -1;
    }

    /**
     * Cancels a Gridlet running in this entity. This method will search the
     * execution, queued and paused list. The User ID is important as many users
     * might have the same Gridlet ID in the lists.
     * <b>NOTE:</b>
     * <ul>
     * <li> Before canceling a Gridlet, this method updates all the Gridlets in
     * the execution list. If the Gridlet has no more MIs to be executed, then
     * it is considered to be <tt>finished</tt>. Hence, the Gridlet can't be
     * canceled.
     *
     * <li> Once a Gridlet has been canceled, it can't be resumed to execute
     * again since this method will pass the Gridlet back to sender, i.e. the
     * <tt>userId</tt>.
     *
     * <li> If a Gridlet can't be found in both execution and paused list, then
     * a <tt>null</tt> Gridlet will be send back to sender, i.e. the
     * <tt>userId</tt>.
     * </ul>
     *
     * @param gridletId a Gridlet ID
     * @param userId the user or owner's ID of this Gridlet
     * @pre gridletId > 0
     * @pre userId > 0
     * @post $none
     */
    @Override
    public synchronized void gridletCancel(int gridletId, int userId) {
        // cancels a Gridlet
        ResGridlet rgl = cancel(gridletId, userId);

        // if the Gridlet is not found
        if (rgl == null) {
            System.out.println(super.resName_
                    + ".SpaceShared.gridletCancel(): Cannot find "
                    + "Gridlet #" + gridletId + " for User #" + userId);

            super.sendCancelGridlet(GridSimTags.GRIDLET_CANCEL, null,
                    gridletId, userId);
            return;
        }

        // if the Gridlet has finished beforehand then prints an error msg
        if (rgl.getGridletStatus() == Gridlet.SUCCESS) {
            System.out.println(super.resName_
                    + ".SpaceShared.gridletCancel(): Cannot cancel"
                    + " Gridlet #" + gridletId + " for User #" + userId
                    + " since it has FINISHED.");
        }

        // sends the Gridlet back to sender
        rgl.finalizeGridlet();
        super.sendCancelGridlet(GridSimTags.GRIDLET_CANCEL, rgl.getGridlet(),
                gridletId, userId);
    }

    /**
     * Pauses a Gridlet only if it is currently executing. This method will
     * search in the execution list. The User ID is important as many users
     * might have the same Gridlet ID in the lists.
     *
     * @param gridletId a Gridlet ID
     * @param userId the user or owner's ID of this Gridlet
     * @param ack an acknowledgement, i.e. <tt>true</tt> if wanted to know
     * whether this operation is success or not, <tt>false</tt>
     * otherwise (don't care)
     * @pre gridletId > 0
     * @pre userId > 0
     * @post $none
     */
    @Override
    public synchronized void gridletPause(int gridletId, int userId, boolean ack) {
        boolean status = false;

        // Find in EXEC List first
        int found = gridletInExecList_.indexOf(gridletId, userId);
        if (found >= 0) {
            // updates all the Gridlets first before pausing
            updateGridletProcessing();

            // Removes the Gridlet from the execution list
            ResGridlet rgl = (ResGridlet) gridletInExecList_.remove(found);

            // if a Gridlet is finished upon cancelling, then set it to success
            // instead.
            if (rgl.getRemainingGridletLength() == 0.0) {
                //found = -1;  // meaning not found in Queue List
                gridletFinish(rgl, Gridlet.SUCCESS);
                System.out.println(super.resName_
                        + ".SpaceShared.gridletPause(): Cannot pause"
                        + " Gridlet #" + gridletId + " for User #" + userId
                        + " since it has FINISHED.");
            } else {
                status = true;
                rgl.setGridletStatus(Gridlet.PAUSED);  // change the status
                gridletPausedList_.add(rgl);   // add into the paused list

                // Set the PE on which Gridlet finished to FREE
                super.resource_.setStatusPE(PE.FREE, rgl.getMachineID(),
                        rgl.getPEID());

                // empty slot is available, hence process a new Gridlet
                allocateQueueGridlet();
            }
        } else {      // Find in QUEUE list
            //found = gridletQueueList_.indexOf(gridletId, userId);
        }

        // if found in the Queue List
//        if (status == false && found >= 0) {
//            status = true;
//
//            // removes the Gridlet from the Queue list
//            ResGridlet rgl = (ResGridlet) gridletQueueList_.remove(found);
//            rgl.setGridletStatus(Gridlet.PAUSED);   // change the status
//            gridletPausedList_.add(rgl);            // add into the paused list
//        } // if not found anywhere in both exec and paused lists
//        else if (found == -1) {
//            System.out.println(super.resName_
//                    + ".SpaceShared.gridletPause(): Error - cannot "
//                    + "find Gridlet #" + gridletId + " for User #" + userId);
//        }
//
//        // sends back an ack if required
//        if (ack) {
//            super.sendAck(GridSimTags.GRIDLET_PAUSE_ACK, status,
//                    gridletId, userId);
//        }
    }

    /**
     * Moves a Gridlet from this GridResource entity to a different one. This
     * method will search in both the execution and paused list. The User ID is
     * important as many Users might have the same Gridlet ID in the lists.
     * <p>
     * If a Gridlet has finished beforehand, then this method will send back the
     * Gridlet to sender, i.e. the <tt>userId</tt> and sets the acknowledgment
     * to false (if required).
     *
     * @param gridletId a Gridlet ID
     * @param userId the user or owner's ID of this Gridlet
     * @param destId a new destination GridResource ID for this Gridlet
     * @param ack an acknowledgement, i.e. <tt>true</tt> if wanted to know
     * whether this operation is success or not, <tt>false</tt>
     * otherwise (don't care)
     * @pre gridletId > 0
     * @pre userId > 0
     * @pre destId > 0
     * @post $none
     */
    @Override
    public synchronized void gridletMove(int gridletId, int userId, int destId, boolean ack) {
        // cancels the Gridlet
        ResGridlet rgl = cancel(gridletId, userId);

        // if the Gridlet is not found
        if (rgl == null) {
            System.out.println(super.resName_
                    + ".SpaceShared.gridletMove(): Cannot find "
                    + "Gridlet #" + gridletId + " for User #" + userId);

            if (ack) // sends back an ack if required
            {
                super.sendAck(GridSimTags.GRIDLET_SUBMIT_ACK, false,
                        gridletId, userId);
            }

            return;
        }

        // if the Gridlet has finished beforehand
        if (rgl.getGridletStatus() == Gridlet.SUCCESS) {
            System.out.println(super.resName_
                    + ".SpaceShared.gridletMove(): Cannot move Gridlet #"
                    + gridletId + " for User #" + userId
                    + " since it has FINISHED.");

            if (ack) // sends back an ack if required
            {
                super.sendAck(GridSimTags.GRIDLET_SUBMIT_ACK, false,
                        gridletId, userId);
            }

            gridletFinish(rgl, Gridlet.SUCCESS);
        } else // otherwise moves this Gridlet to a different GridResource
        {
            rgl.finalizeGridlet();

            // Set PE on which Gridlet finished to FREE
            super.resource_.setStatusPE(PE.FREE, rgl.getMachineID(),
                    rgl.getPEID());

            super.gridletMigrate(rgl.getGridlet(), destId, ack);
            allocateQueueGridlet();
        }
    }

    /**
     * Resumes a Gridlet only in the paused list. The User ID is important as
     * many Users might have the same Gridlet ID in the lists.
     *
     * @param gridletId a Gridlet ID
     * @param userId the user or owner's ID of this Gridlet
     * @param ack an acknowledgement, i.e. <tt>true</tt> if wanted to know
     * whether this operation is success or not, <tt>false</tt>
     * otherwise (don't care)
     * @pre gridletId > 0
     * @pre userId > 0
     * @post $none
     */
    @Override
    public synchronized void gridletResume(int gridletId, int userId, boolean ack) {
        boolean status = false;

        // finds the Gridlet in the execution list first
        int found = gridletPausedList_.indexOf(gridletId, userId);
        if (found >= 0) {
            // removes the Gridlet
            ResGridlet rgl = (ResGridlet) gridletPausedList_.remove(found);
            rgl.setGridletStatus(Gridlet.RESUMED);

            // update the Gridlets up to this point in time
            updateGridletProcessing();
            status = true;

            // if there is an available PE slot, then allocate immediately
            boolean success = false;
            if (gridletInExecList_.size() < super.totalPE_) {
                //success = allocatePEtoGridlet(rgl);
            }

            // otherwise put into Queue list
            if (!success) {
                rgl.setGridletStatus(Gridlet.QUEUED);
                //gridletQueueList_.add(rgl);
            }

            System.out.println(super.resName_ + "TimeShared.gridletResume():"
                    + " Gridlet #" + gridletId + " with User ID #"
                    + userId + " has been sucessfully RESUMED.");
        } else {
            System.out.println(super.resName_
                    + "TimeShared.gridletResume(): Cannot find "
                    + "Gridlet #" + gridletId + " for User #" + userId);
        }

        // sends back an ack if required
        if (ack) {
            super.sendAck(GridSimTags.GRIDLET_RESUME_ACK, status,
                    gridletId, userId);
        }
    }

    ///////////////////////////// PRIVATE METHODS /////////////////////
    /**
     * Allocates the first Gridlet in the Queue list (if any) to execution list
     *
     * @pre $none
     * @post $none
     */
    private void allocateQueueGridlet() {
        // if there are many Gridlets in the QUEUE, then allocate a
        // PE to the first Gridlet in the list since it follows FCFS
        // (First Come First Serve) approach. Then removes the Gridlet from
        // the Queue list
        boolean allocatedFromQueue;
        ResGridlet obj;
        do {
            allocatedFromQueue = false;
            if (!gridletLongQueueList_.isEmpty()) {
                obj = (ResGridlet) gridletLongQueueList_.get(0);
                if (allocatePEtoGridlet(obj, PartitionLong)) {
                    allocatedFromQueue = true;
                    gridletLongQueueList_.remove(obj);
                    continue; // try to allocated another long
                }
            }

            if (!gridletMediumQueueList_.isEmpty()) {
                obj = (ResGridlet) gridletMediumQueueList_.get(0);
                if (allocatePEtoGridlet(obj, PartitionMedium)) {
                    allocatedFromQueue = true;
                    gridletMediumQueueList_.remove(obj);
                }
            }
        } while (allocatedFromQueue);
    }

    /**
     * Updates the execution of all Gridlets for a period of time. The time
     * period is determined from the last update time up to the current time.
     * Once this operation is successfull, then the last update time refers to
     * the current time.
     *
     * @pre $none
     * @post $none
     */
    private synchronized void updateGridletProcessing() {
        // Identify MI share for the duration (from last event time)
        double time = GridSim.clock();
        double timeSpan = time - lastUpdateTime_;

        // if current time is the same or less than the last update time,
        // then ignore
        if (timeSpan <= 0.0) {
            return;
        }

        // Update Current Time as Last Update
        lastUpdateTime_ = time;

        // update the GridResource load
        int size = gridletInExecList_.size();
        double load = super.calculateTotalLoad(size);
        super.addTotalLoad(load);

        // if no Gridlets in execution then ignore the rest
        if (size == 0) {
            return;
        }

        ResGridlet obj;

        // a loop that allocates MI share for each Gridlet accordingly
        Iterator iter = gridletInExecList_.iterator();
        while (iter.hasNext()) {
            obj = (ResGridlet) iter.next();

            // Updates the Gridlet length that is currently being executed
            load = getMIShare(timeSpan);
            obj.updateGridletFinishedSoFar(load);
        }
        int machineCount = 0;
        for (Machine m : resource_.getMachineList()) {
            if (m.getNumBusyPE() > 0)
                machineCount++;
        }

        //Print status to trace file
        double span = time - lastPrintedTrace;
        if (span > minimumSpan) {
            res_trace.print(time + ",");
            res_trace.print(resource_.getNumBusyPE() + ",");
            res_trace.print(gridletInExecList_.size() + ",");
            res_trace.print(gridletMediumQueueList_.size() + ",");
            res_trace.println(gridletLongQueueList_.size() + ",");
            res_trace.println(machineCount);
            lastPrintedTrace = time;
        }
    }

    /**
     * Identifies MI share (max and min) each Gridlet gets for a given timeSpan
     *
     * @param timeSpan duration
     * @param machineId machine ID that executes this Gridlet
     * @return the total MI share that a Gridlet gets for a given
     * <tt>timeSpan</tt>
     * @pre timeSpan >= 0.0
     * @pre machineId > 0
     * @post $result >= 0.0
     */
    private double getMIShare(double timeSpan) {
        // 1 - localLoad_ = available MI share percentage
        double localLoad = super.resCalendar_.getCurrentLoad();

        // each Machine might have different PE Rating compare to another
        // so much look at which Machine this PE belongs to
        // 100.0 is for the MIPs rating for the PEs     ~RenatoCJN
        double totalMI = 100.0 * timeSpan * (1 - localLoad);
        return totalMI;
    }

    /**
     * Allocates a Gridlet into a free PE and sets the Gridlet status into
     * INEXEC and PE status into busy afterwards
     *
     * @param rgl a ResGridlet object
     * @return <tt>true</tt> if there is an empty PE to process this Gridlet,
     * <tt>false</tt> otherwise
     * @pre rgl != null
     * @post $none
     */
    private synchronized boolean allocatePEtoGridlet(ResGridlet rgl, int partition) {
        // IDENTIFY MACHINE whi ch has a free PE and add this Gridlet to it.
        if (rgl.getNumPE() > maxPeSize) {
            return false;
        }

        int countFreeMachine = 0;
        double requiredMachines = Math.ceil(rgl.getNumPE() / pePerMachine);
        //System.out.println(rgl.getNumPE() + " PEs => " + requiredMachines + " machines");
        
        ArrayList<Machine> machines = new ArrayList<>();
        for (Machine m : resource_.getMachineList()) {
            if (m.getNumBusyPE() == 0) {
                countFreeMachine++;
                if (partition == PartitionLong ||
                        mediumPartitionIds.contains(m.getMachineID())) {
                    machines.add(m);
                }
            }
        }
        if (machines.size() < requiredMachines) {
            return false;
        }

        //System.out.print(GridSim.clock() + " " + resource_.getNumFreePE()+ " - " + rgl.getNumPE() + " = ");
        int allocatedPEs = 0;
        //System.out.print("machines: ");
        for (Machine m : machines) {
            for (PE freePE : m.getPEList()) {
                if (allocatedPEs == rgl.getNumPE()) {
                    break;
                }
                // Register PE and machine to gridlet
                rgl.setMachineAndPEID(m.getMachineID(), freePE.getID());

                // Set allocated PE to BUSY status
                resource_.setStatusPE(PE.BUSY, m.getMachineID(),
                        freePE.getID());

                allocatedPEs++;
            }
            //System.out.print(m.getMachineID() + " ");
            if (allocatedPEs == rgl.getNumPE()) {
                break;
            }
        }

        //System.out.println(resource_.getNumFreePE()+" / "+countFreeMachine);
        //System.out.println("| Required machines: "+requiredMachines);
        //System.out.printf("allocatedPEs: %d | requiredPes: %d\n\n", allocatedPEs, rgl.getNumPE());
        // change Gridlet status
        rgl.setGridletStatus(Gridlet.INEXEC);

        // add this Gridlet into execution list
        gridletInExecList_.add(rgl);

        // Identify Completion Time and Set Interrupt
        int rating = 100;
        double time = forecastFinishTime(rating,
                rgl.getRemainingGridletLength());

        int roundUpTime = (int) (time + 1);   // rounding up
        rgl.setFinishTime(roundUpTime);
        // then send this into itself
        super.sendInternalEvent(roundUpTime);
        return true;
    }

    /**
     * Forecast finish time of a Gridlet.
     * <tt>Finish time = length / available rating</tt>
     *
     * @param availableRating the shared MIPS rating for all Gridlets
     * @param length remaining Gridlet length
     * @return Gridlet's finish time.
     * @pre availableRating >= 0.0
     * @pre length >= 0.0
     * @post $none
     */
    private static double forecastFinishTime(double availableRating, double length) {
        double finishTime = (length / availableRating);

        // This is as a safeguard since the finish time can be extremely
        // small close to 0.0, such as 4.5474735088646414E-14. Hence causing
        // some Gridlets never to be finished and consequently hang the program
        if (finishTime < 1.0) {
            finishTime = 1.0;
        }

        return finishTime;
    }

    /**
     * Checks all Gridlets in the execution list whether they are finished or
     * not.
     *
     * @pre $none
     * @post $none
     */
    private synchronized void checkGridletCompletion() {
        ResGridlet obj;
        int i = 0;

        // NOTE: This one should stay as it is since gridletFinish()
        // will modify the content of this list if a Gridlet has finished.
        // Can't use iterator since it will cause an exception
        while (i < gridletInExecList_.size()) {
            obj = (ResGridlet) gridletInExecList_.get(i);

            if (obj.getRemainingGridletLength() == 0.0) {
                gridletInExecList_.remove(obj);
                gridletFinish(obj, Gridlet.SUCCESS);
                continue;
            }

            i++;
        }

        // if there are still Gridlets left in the execution
        // then send this into itself for an hourly interrupt
        // NOTE: Setting the internal event time too low will make the
        //       simulation more realistic, BUT will take longer time to
        //       run this simulation. Also, size of sim_trace will be HUGE!
        if (gridletInExecList_.size() > 0) {
            super.sendInternalEvent(60.0 * 60.0);
        }
    }

    /**
     * Updates the Gridlet's properties, such as status once a Gridlet is
     * considered finished.
     *
     * @param rgl a ResGridlet object
     * @param status the Gridlet status
     * @pre rgl != null
     * @pre status >= 0
     * @post $none
     */
    private void gridletFinish(ResGridlet rgl, int status) {
        // Set PE on which Gridlet finished to FREE
        //System.out.print(GridSim.clock() + " " + resource_.getNumFreePE() + " + " + rgl.getNumPE() + " = ");
        if (rgl.getNumPE() > 1) {
            for (int i=0; i<rgl.getListPEID().length; i++) {
                int m = rgl.getListMachineID()[i];
                int pe = rgl.getListPEID()[i];
                resource_.setStatusPE(PE.FREE, m, pe);
            }
        } else {
            super.resource_.setStatusPE(PE.FREE, rgl.getMachineID(), rgl.getPEID());
        }
        //System.out.println(resource_.getNumFreePE());

        // the order is important! Set the status first then finalize
        // due to timing issues in ResGridlet class
        rgl.setGridletStatus(status);
        rgl.finalizeGridlet();
        super.sendFinishGridlet(rgl.getGridlet());

        allocateQueueGridlet();   // move Queued Gridlet into exec list
    }

    /**
     * Handles an operation of canceling a Gridlet in either execution list or
     * paused list.
     *
     * @param gridletId a Gridlet ID
     * @param userId the user or owner's ID of this Gridlet
     * @return an ResGridlet object <tt>null</tt> if this Gridlet is not found
     * @pre gridletId > 0
     * @pre userId > 0
     * @post $none
     */
    private ResGridlet cancel(int gridletId, int userId) {
        ResGridlet rgl = null;

        // Find in EXEC List first
        int found = gridletInExecList_.indexOf(gridletId, userId);
        if (found >= 0) {
            // update the gridlets in execution list up to this point in time
            updateGridletProcessing();

            // Get the Gridlet from the execution list
            rgl = (ResGridlet) gridletInExecList_.remove(found);

            // if a Gridlet is finished upon cancelling, then set it to success
            // instead.
            if (rgl.getRemainingGridletLength() == 0.0) {
                rgl.setGridletStatus(Gridlet.SUCCESS);
            } else {
                rgl.setGridletStatus(Gridlet.CANCELED);
            }

            // Set PE on which Gridlet finished to FREE
            super.resource_.setStatusPE(PE.FREE, rgl.getMachineID(),
                    rgl.getPEID());
            allocateQueueGridlet();
            return rgl;
        }

        // Find in QUEUE list
//        found = gridletQueueList_.indexOf(gridletId, userId);
//        if (found >= 0) {
//            rgl = (ResGridlet) gridletQueueList_.remove(found);
//            rgl.setGridletStatus(Gridlet.CANCELED);
//        } // if not, then find in the Paused list
//        else {
//            found = gridletPausedList_.indexOf(gridletId, userId);
//
//            // if found in Paused list
//            if (found >= 0) {
//                rgl = (ResGridlet) gridletPausedList_.remove(found);
//                rgl.setGridletStatus(Gridlet.CANCELED);
//            }
//
//        }
        return rgl;
    }
}
