package es.retalis.mazdaavisos;

import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Debounces frameless-window indexing; missing fields never mean closed. */
final class WindowAlertFilter {
 static final long SETTLE_MS=10_000L;
 private static final String[] WINDOWS={"diverWindow","passengerWindow","leftRearWindow","rightRearWindow"};
 private static final String[] DOORS={"driverDoor","passengerDoor","leftRearDoor","rightRearDoor"};
 private final int[] windows={-1,-1,-1,-1},doors={-1,-1,-1,-1};
 private final long[] openedAt=new long[4],doorClosedAt=new long[4];
 private final boolean[] changedOpen=new boolean[4],qualifiedOpen=new boolean[4],closedPending=new boolean[4];
 static final class Changes {
   final List<Integer> opened=new ArrayList<>(),closed=new ArrayList<>();
 }
 void reset(){
   Arrays.fill(windows,-1);Arrays.fill(doors,-1);Arrays.fill(openedAt,0);Arrays.fill(doorClosedAt,0);
   Arrays.fill(changedOpen,false);Arrays.fill(qualifiedOpen,false);Arrays.fill(closedPending,false);
 }
 void observe(JSONObject raw,long now){
   for(int i=0;i<4;i++){
     Integer door=AutoLockPolicy.code(raw,DOORS[i],0,100);
     if(door!=null){
       int state=door==0?0:1;
       if(state==0&&doors[i]!=0)doorClosedAt[i]=now;
       doors[i]=state;
     }
     Integer value=AutoLockPolicy.code(raw,WINDOWS[i],0,100);
     if(value==null)continue;
     int state=value==0?0:1;
     if(windows[i]==state)continue;
     int before=windows[i];windows[i]=state;
     if(state==1){
       openedAt[i]=now;changedOpen[i]=before==0;qualifiedOpen[i]=false;closedPending[i]=false;
     }else{
       // A short open/close pair caused by a door produces neither notification.
       if(qualifiedOpen[i])closedPending[i]=true;
       openedAt[i]=0;changedOpen[i]=false;qualifiedOpen[i]=false;
     }
   }
 }
 Changes poll(long now){
   Changes result=new Changes();
   for(int i=0;i<4;i++){
     if(closedPending[i]){result.closed.add(i);closedPending[i]=false;}
     if(windows[i]!=1||qualifiedOpen[i]||now-openedAt[i]<SETTLE_MS)continue;
     if(doors[i]==1||doors[i]==0&&now-doorClosedAt[i]<SETTLE_MS)continue;
     qualifiedOpen[i]=true;
     // Initial values establish a baseline, including an initially open window.
     if(changedOpen[i]){result.opened.add(i);changedOpen[i]=false;}
   }
   return result;
 }
 boolean confirmedOpen(int index){return windows[index]==1&&qualifiedOpen[index]&&doors[index]!=1;}
}
