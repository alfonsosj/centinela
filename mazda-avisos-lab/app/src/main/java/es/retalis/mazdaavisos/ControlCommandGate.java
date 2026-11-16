package es.retalis.mazdaavisos;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;

/** Serializes all remote controls and lets a manual request pre-empt a pending auto-lock. */
final class ControlCommandGate {
 static final ControlCommandGate GLOBAL=new ControlCommandGate(()->System.nanoTime()/1_000_000L);
 private final ReentrantLock lock=new ReentrantLock(true);
 private final AtomicLong revision=new AtomicLong();
 private final LongSupplier clock;
 private volatile long manualAt=Long.MIN_VALUE;
 ControlCommandGate(LongSupplier clock){this.clock=clock;}
 boolean quiet(){return manualAt==Long.MIN_VALUE||clock.getAsLong()-manualAt>=60_000L;}
 void beginManual()throws InterruptedException{
   revision.incrementAndGet();manualAt=clock.getAsLong();lock.lockInterruptibly();
 }
 void endManual(){manualAt=clock.getAsLong();lock.unlock();}
 boolean beginAutomatic(){
   if(!quiet()||!lock.tryLock())return false;
   if(!quiet()){lock.unlock();return false;}return true;
 }
 void endAutomatic(){lock.unlock();}
 long revision(){return revision.get();}
 boolean unchanged(long expected){return revision.get()==expected&&quiet();}
}
