package bgu.spl.mics.application.services;

import bgu.spl.mics.application.messages.*;
import bgu.spl.mics.application.objects.GPU.GPUStatus;
import bgu.spl.mics.Event;
import bgu.spl.mics.MicroService;
import bgu.spl.mics.application.objects.GPU;
import bgu.spl.mics.application.objects.Model;

import java.util.LinkedList;

/**
 * GPU service is responsible for handling the
 * {@link TrainModelEvent} and {@link TestModelEvent}.
 *
 */
public class GPUService extends MicroService {

    private boolean isBusy;
    private Model currModel;
    private GPU GPU;
    private TrainModelEvent currEvent;
    private LinkedList<Event<Model>> eventsInLine;
    private int total = 0;

    public GPUService(String name,GPU gpu) {
        super(name);
        GPU= gpu;
        isBusy=false;
        eventsInLine= new LinkedList<>();
    }

    @Override
    protected void initialize() {
        subscribeBroadcast(TerminationBroadcast.class, c -> terminate());

        subscribeBroadcast(TickBroadcast.class, c ->{
            GPU.updateTick();
            if(GPU.getStatus()==GPUStatus.Completed){
                GPU.setStatus(GPUStatus.Waiting);
                currModel= GPU.getModel();
                complete(currEvent, currModel);
                if(!eventsInLine.isEmpty()){
                    startProcessingEvent();
                }
                else{
                    isBusy=false;
                }
            }

        } );
        subscribeEvent(TrainModelEvent.class, c -> {
            if(!isBusy){ //starts to train a new model
                startProcessingTrainEvent(c);
            }
            else{
                //inserts the event to eventsInLine list, sorted by size for efficiency
                int i = 0;
                int size = c.getModel().getData().getSize();
                for(Event<Model> m : eventsInLine){
                    if(m.getClass() == TrainModelEvent.class){
                        if(((TrainModelEvent) m).getModel().getData().getSize() < size){
                            i++;
                        }
                    }
                }
                eventsInLine.add(i, c); //saves the event for when it finishes training this model*/
                //eventsInLine.add(c);
            }
        });

        subscribeEvent(TestModelEvent.class, c -> {
            if(!isBusy){
                startProcessingTestEvent(c);
            }
            else{
                eventsInLine.addFirst(c);
            }
        });

    }

    private void startProcessingEvent() {
        isBusy=true;
        Event<Model> e= eventsInLine.poll();
        if(e!=null && e.getClass()==TrainModelEvent.class){
            startProcessingTrainEvent((TrainModelEvent) e);
        }
        else if(e!=null && e.getClass()==TestModelEvent.class){
            startProcessingTestEvent((TestModelEvent) e);
        }
    }

    private void startProcessingTrainEvent(TrainModelEvent c){
        isBusy=true;
        GPU.trainModel(c.getModel());
        currModel=c.getModel();
        currEvent= c;
    }

    private void startProcessingTestEvent(TestModelEvent c){
        isBusy=true;
        GPU.testModel(c.getModel());
        currModel= c.getModel();
        complete(c, currModel);
        isBusy=false;
    }

}