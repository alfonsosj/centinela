package es.retalis.mazdaavisos;
final class ControlSession {
 static final class State { String vehicleId="PRIVATE-CAR-007",deviceId="PRIVATE-DEVICE-008",token="PRIVATE-USER|PRIVATE-TSP-TOKEN"; }
 static State current;
 static State get(){return current;}
}
