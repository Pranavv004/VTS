package com.example.Server.model;

public record LoginPacket (
	String header,
    String vehicleRegNo,
    String imei,
    String firmwareVersion,
    String protocolIdentifier,
    double latitude,
    String latitudeDir,
    double longitude,
    String longitudeDir
){
	
}