package es.retalis.mazdaavisos;
import org.json.JSONObject;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AlertBehaviorChecks {
 static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
 static JSONObject all(){return new JSONObject().put("carStatus",2).put("driverDoorLock",1)
  .put("driverDoor",0).put("passengerDoor",0).put("leftRearDoor",0).put("rightRearDoor",0).put("trunk",0);}
 static AutoLockPolicy fresh(long time){AutoLockPolicy p=new AutoLockPolicy();p.connection(true);p.enabled(true,time);p.observe(all(),time);return p;}
 public static void main(String[] args)throws Exception{
  WindowAlertFilter windows=new WindowAlertFilter();
  windows.observe(new JSONObject().put("driverDoor",0).put("diverWindow",0),1000);
  windows.observe(new JSONObject().put("diverWindow",1),2000);
  windows.observe(new JSONObject().put("driverDoor",1),2300);
  check(windows.poll(30000).opened.isEmpty(),"No window alert while door is open");
  windows.observe(new JSONObject().put("driverDoor",0),31000);
  check(windows.poll(35000).opened.isEmpty(),"Wait for door to settle");
  windows.observe(new JSONObject().put("diverWindow",0),36000);
  WindowAlertFilter.Changes pair=windows.poll(50000);
  check(pair.opened.isEmpty()&&pair.closed.isEmpty(),"Indexing pair emits neither open nor close");
  windows.observe(new JSONObject().put("diverWindow",50),60000);
  check(windows.poll(69999).opened.isEmpty(),"Wait full stable interval");
  check(windows.poll(70000).opened.size()==1&&windows.confirmedOpen(0),"Persistent opening is reported");
  check(windows.poll(90000).opened.isEmpty(),"No duplicate opening");
  windows.observe(new JSONObject().put("diverWindow",0),91000);
  check(windows.poll(91000).closed.size()==1&&!windows.confirmedOpen(0),"Confirmed opening closes once");
  windows.reset();windows.observe(new JSONObject().put("diverWindow",1).put("driverDoor",1),1000);
  windows.observe(new JSONObject().put("diverWindow",0).put("driverDoor",0),3000);
  check(windows.poll(20000).closed.isEmpty(),"Initial indexing is not a closing alert");
  windows.reset();windows.observe(new JSONObject().put("diverWindow",0).put("passengerWindow",0),1000);
  windows.observe(new JSONObject().put("diverWindow",40).put("passengerWindow",40),2000);
  check(windows.poll(12000).opened.size()==2,"Group persistent windows");
  windows.reset();windows.observe(new JSONObject().put("diverWindow",true),1000);
  check(!windows.confirmedOpen(0)&&windows.poll(20000).opened.isEmpty(),"Invalid/unknown window is not open");

  AutoLockPolicy policy=fresh(1000);
  check(policy.claim(60999)==null,"No lock before one minute");
  AutoLockPolicy.Attempt a=policy.claim(61000);check(a!=null&&policy.permits(a,61000),"Lock eligible at minute");
  check(policy.claim(61001)==null,"One in-flight attempt");
  policy.observe(new JSONObject().put("driverDoor",1),62000);
  check(!policy.permits(a,62000),"Opening door cancels queued command");policy.finish(a,false,62000);
  policy.observe(all(),120000);
  check(policy.claim(129999)==null,"Wait ten seconds after final door closes");
  a=policy.claim(130000);check(a!=null,"Can retry once settled with fresh data");
  check(policy.finish(a,true,131000),"Confirmed lock is accepted");
  check(policy.claim(220000)==null,"Confirmed lock ends retries");
  policy.observe(all(),230000);check(policy.claim(289999)==null,"A later unlock starts a new minute");
  a=policy.claim(290000);check(a!=null,"New unlock cycle can lock");
  policy.connection(false);check(!policy.permits(a,290001),"Disconnected monitor cancels pending action");
  policy.connection(true);check(policy.claim(390000)==null,"Reconnect cannot reuse old closed states");
  policy=fresh(1000);check(policy.claim(121001)==null,"Stale MQTT cannot authorize lock");
  policy.observe(new JSONObject().put("carStatus",2).put("driverDoorLock",1),122000);
  check(policy.claim(122000)==null,"Fresh car state does not refresh stale door evidence");
  JSONObject missing=all();missing.remove("rightRearDoor");policy=new AutoLockPolicy();policy.connection(true);policy.enabled(true,1000);policy.observe(missing,1000);
  check(policy.claim(61000)==null,"Unknown door never defaults to closed");
  policy=fresh(1000);policy.observe(new JSONObject().put("trunk",1),2000);check(policy.claim(61000)==null,"Open trunk blocks lock");
  policy=fresh(1000);policy.observe(new JSONObject().put("carStatus",1),2000);check(policy.claim(61000)==null,"Driving blocks lock");
  policy=fresh(1000);a=policy.claim(61000);policy.enabled(false,62000);check(!policy.permits(a,62000),"Disabling cancels pending lock");
  policy=fresh(1000);
  for(int i=0;i<3;i++){long now=61000+i*60000L;policy.observe(all(),now);a=policy.claim(now);check(a!=null,"Bounded retry eligible");policy.finish(a,false,now);check(policy.claim(now+59999)==null,"Retry interval enforced");}
  policy.observe(all(),241000);check(policy.claim(241000)==null&&policy.attempts()==3,"Three failed attempts maximum");
  policy.authorizationChanged(242000);policy.observe(all(),302000);check(policy.claim(302000)!=null,"New authorization can rearm failed cycle");
  check(AutoLockPolicy.code(new JSONObject().put("x",0.5),"x",0,1)==null&&AutoLockPolicy.code(new JSONObject().put("x",false),"x",0,1)==null,"No coercion of fractional or boolean states");

  long[] clock={1000};ControlCommandGate gate=new ControlCommandGate(()->clock[0]);
  check(gate.beginAutomatic(),"Auto can acquire idle gate");long revision=gate.revision();
  CountDownLatch started=new CountDownLatch(1);AtomicBoolean acquired=new AtomicBoolean();
  Thread manual=new Thread(()->{started.countDown();try{gate.beginManual();acquired.set(true);gate.endManual();}catch(InterruptedException failure){throw new AssertionError(failure);}});
  manual.start();check(started.await(2,TimeUnit.SECONDS),"Manual thread starts");
  long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);while(gate.revision()==revision&&System.nanoTime()<deadline)Thread.yield();
  check(!gate.unchanged(revision)&&!acquired.get(),"Manual request invalidates auto before waiting for gate");
  gate.endAutomatic();manual.join(2000);check(acquired.get()&&!manual.isAlive(),"Controls serialize without deadlock");
  check(!gate.beginAutomatic(),"Manual control pauses auto for one minute");clock[0]=61000;
  check(gate.beginAutomatic(),"Auto resumes after manual grace period");gate.endAutomatic();
  System.out.println("PASS: frameless window filtering, baselines, stable changes, auto-lock timing/closures/freshness, cancellation, bounded retry, authorization renewal and manual priority.");
 }
}
