/***********************************************************************************************************************
 *  Copyright 2020 Brian Schmitt
 *
 *  Contributors:
 *      https://github.com/molobrakos/volvooncall   Python implemention of Volvo On Call
 *      https://github.com/andrew0x01/hubitat       Original Hubitat Implementation
 *
 *  Volvo On Call (VOC) Driver
 *
 *  Author: Andrew Filby, c
 *
 *
 *  Features:
 *   - Provides an implementation of the Volvo On Call facilities for Hubitat
 *   - Car attributes
 *   - Location for presence
 *   - Lock, Unlock, Preclimatization, Blink Lights, Sound Horn
 *
 *
 ***********************************************************************************************************************/

public static String version()      {  return "v1.0.2"  }

/***********************************************************************************************************************
 *
 * Version: 1.0.0 (Andrew Filby)
 *   18/2/2019: initial release.
 *
 * Version: 1.0.1 (Andrew Filby)
 *   27/4/2019: Updated for vehicles without hvBattery and added GPS attributes suggested by chrbratt
 *
 * Version: 1.0.2 ((Andrew Filby))
 *   27/1/2020: Added Switch, Lock, and Battery Capabilities
 *                'Battery' will report fuel level
 *                Added Remote Start Duration as a configuration setting
 *                Added Package & Import Support
 *
 */

import groovy.transform.Field

metadata {
    definition(name: "Volvo On Call",
        namespace: "filby",
        author: "Andrew Filby, Brian Schmitt",
        importUrl: "https://raw.githubusercontent.com/brianschmitt/hubitat-voc/master/drivers/Volvo-On-Call.groovy") {

        capability "Switch"
        capability "Lock"
        capability "Refresh"
        capability "Sensor"
        capability "Presence Sensor"
        capability "Battery"

        attribute "averageFuelConsumption", "number"
        attribute "averageSpeed", "number"
        attribute "brakeFluid", "string"
        attribute "carLocked", "string"
        attribute "connectionStatus", "string"
        attribute "distanceToEmpty", "number"
        attribute "tailgateOpen", "string"
        attribute "rearRightDoorOpen", "string"
        attribute "rearLeftDoorOpen", "string"
        attribute "frontRightDoorOpen", "string"
        attribute "frontLeftDoorOpen", "string"
        attribute "hoodOpen", "string"
        attribute "engineRunning", "string"
        attribute "fuelAmount", "number"
        attribute "fuelAmountLevel", "number"
        attribute "hvBatteryChargeStatusDerived", "string"
        attribute "hvBatteryChargeModeStatus", "string"
        attribute "hvBatteryChargeStatus", "string"
        attribute "hvBatteryLevel", "number"
        attribute "distanceToHVBatteryEmpty", "number"
        attribute "hvBatteryChargeWarning", "string"
        attribute "timeToHVBatteryFullyCharged", "number"
        attribute "odometer", "number"
        attribute "remoteClimatizationStatus", "string"
        attribute "serviceWarningStatus", "string"
        attribute "theftAlarm", "string"
        attribute "tripMeter1", "number"
        attribute "tripMeter2", "number"
        attribute "frontLeftTyrePressure", "string"
        attribute "frontRightTyrePressure", "string"
        attribute "rearLeftTyrePressure", "string"
        attribute "rearRightTyrePressure", "string"
        attribute "washerFluidLevel", "string"
        attribute "frontLeftWindowOpen", "string"
        attribute "frontRightWindowOpen", "string"
        attribute "rearLeftWindowOpen", "string"
        attribute "rearRightWindowOpen", "string"
        attribute "longitude", "number"
        attribute "latitude", "number"
        attribute "speed", "number"
        attribute "heading", "string"
        attribute "positions", "number"
        attribute "GMaps Link", "string"

        command "refresh"
        command "preclimatization"
        command "log_attributes"
        command "blinkLights"
        command "soundHorn"
    }

    preferences {
        section {
            input(
                type: "text",
                name: "userName",
                title: "User name",
                required: true
            )
            input(
                type: "text",
                name: "password",
                title: "Password",
                required: true
            )
            input(
                type: "text",
                name: "vin",
                title: "VIN",
                required: true
            )
            input(
                type: text,
                name: "region",
                title: "Region (e.g. na,cn)",
                required: false,
                defaultValue: ""
            )
            input(
                type: "enum",
                name: "autoPoll",
                title: "Enable auto polling?",
                required: true,
                defaultValue: "Yes",
                options: ["Yes": "Yes", "No": "No"]
            )
            input(
                type: "enum",
                name: "pollEvery",
                title: "Auto poll interval",
                required: true,
                defaultValue: 5,
                options: [1: "1 minute", 5: "5 minutes", 10: "10 minutes", 15: "15 minutes", 30: "30 minutes"]
            )
            input(
                type: "enum",
                name: "engineStartDuration",
                title: "Remote Start Duration",
                required: true,
                defaultValue: 10,
                options: [5: "5 minutes", 10: "10 minutes", 15: "15 minutes"]
            )
            input(
                type: "enum",
                name: "debugLogging",
                title: "Enable debug logging?",
                required: true,
                defaultValue: "Yes",
                options: ["Yes": "Yes", "No": "No"]
            )
        }
    }
}

def refresh() {
    log "refresh()"
    poll()
}

def configure() {
    log "configure()"
    poll()
}

def updated() {
    log "updated()"
    poll()
    unschedule()
    if (autoPoll == "Yes") {
        if (pollEvery == "1") {
            runEvery1Minute(poll)
        } else {
            "runEvery${pollEvery}Minutes" (poll)
        }
    }
}

def log(msg) {
    if (debugLogging == "Yes") {
        log.debug(msg)
    }
}

def poll() {
    log.info "${device.displayName} executing poll"
    getVOCdata()
    getPosition()

    return
}

def blinkLights() {
    postVOC("honk_blink/lights")
}

def soundHorn() {
    postVOC("honk_blink/horn")
}

def preclimatization() {
    postVOC("preclimatization/start")
}

def lock() {
    postVOC("lock")
}

def unlock() {
    postVOC("unlock")
}

def on(){
    postVOC("engine/start")
    sendEvent(name: "switch", value: "on", displayed: true)
    getVOCdata()
}

def off(){
    postVOC("engine/stop")
    sendEvent(name: "switch", value: "off", displayed: true)
    getVOCdata()
}

def postVOC(command) {

    log.info "${device.displayName} executing ${command} command"

    // Get the car position as it is required for some of the commands
    def pos = [:]
    def posParams = getURL("position?client_longitude=-00.00&client_precision=5.000000&client_latitude=00.000")
    try {
        httpGet(posParams) {
            resp ->
                if (resp?.data) pos << resp.data;
                else log.error "http call for position api did not return data: ${resp1}";
        }
    } catch (e) {
        log.error "httpGet call failed for VOC api: ${e}"
    }

    if (!pos) {
        log.warn "No response from VOC API"
        return
    }

    def obs = [:]
    def params = [
        uri: getURI(command),
        headers: ["authorization": authHeader(),
            "X-Device-Id": "Device",
            "X-OS-Type": "Android",
            "X-Originator-Type": "App",
            "X-OS-Version": "22",
            "Content-Type": "application/json"
        ],
        body: (command == "engine/start")
                ? """{"runtime":"${engineStartDuration}"}""" 
                : """{"clientAccuracy":0,"clientLatitude":${pos.position.latitude},"clientLongitude":${pos.position.longitude}}"""
    ]

    log params

    try {
        httpPost(params) {
            resp ->
                if (resp?.data) obs << resp.data;
                else log.error "http call for VOC api did not return data: $resp";
        }
    } catch (e) {
        log.error "httpPost call failed for VOC api ${command}: ${e}"
    }

    if (!obs) {
        log.warn "No response from VOC API for command ${command}"
        return
    }
    log obs

    if (obs.status != "Started" && obs.status != "Queued") {
        log.warn "Failed to execute ${command}, status = ${obs.status}"
        return
    }

    log.info "${device.displayName} ${command} command status : ${obs.status}"

}

private log_attributes() {
    def obs = [:]
    def params = getURL("attributes")

    log.info "${device.displayName} getting vehicle attributes"

    try {
        httpGet(params) {
            resp ->
                if (resp?.data) obs << resp.data;
                else log.error "http call for VOC api did not return data: $resp";
        }
    } catch (e) {
        log.error "httpGet call failed for VOC api: ${e}"
    }

    if (!obs) {
        log.warn "No response from VOC API"
        return
    }

    log.info "Registration number : ${obs.registrationNumber}"
    log.info "Vehicle Id Number (VIN) : ${obs.vin}"
    log.info "Vehicle type : ${obs.vehicleType}"
    log.info "Honk and blink supported : ${obs.honkAndBlinkSupported}"
    log.info "Remote heater supported : ${obs.remoteHeaterSupported}"
    log.info "Unlock supported : ${obs.unlockSupported}"
    log.info "Lock supported : ${obs.lockSupported}"
    log.info "Pre-climatization supported : ${obs.preclimatizationSupported}"
    log.info "Engine start supported : ${obs.engineStartSupported}"
    log.ingo "Journal Log supported : ${obs.journalLogSupported}"

    log obs

    return obs
}

private getVOCdata() {
    def obs = [:]
    def params = getURL("status")

    log.info "${device.displayName} getting status"

    try {
        httpGet(params) {
            resp ->
                if (resp?.data) obs << resp.data;
                else log.error "http call for VOC api did not return data: ${resp}";
        }
    } catch (e) {
        log.error "httpGet call failed for VOC api: ${e}"
    }

    if (!obs) {
        log.warn "No response from VOC API"
        sendEvent(name: "lock", value: "unknown", displayed: true)
        return
    }

    log obs

    def now = new Date().format('yyyy-MM-dd HH:mm', location.timeZone)
    sendEvent(name: "lastVOCupdate", value: now, displayed: true)
    sendEvent(name: "averageFuelConsumption", value: obs.averageFuelConsumption, displayed: true)
    sendEvent(name: "averageSpeed", value: obs.averageSpeed, displayed: true)
    sendEvent(name: "brakeFluid", value: obs.brakeFluid, displayed: true)
    sendEvent(name: "carLocked", value: obs.carLocked, displayed: true)
    sendEvent(name: "lock", value: obs.carLocked ? "locked" : "unlocked", displayed: true)
    sendEvent(name: "connectionStatus", value: obs.connectionStatus, displayed: true)
    sendEvent(name: "distanceToEmpty", value: obs.distanceToEmpty, displayed: true)
    sendEvent(name: "tailgateOpen", value: obs.doors.tailgateOpen, displayed: true)
    sendEvent(name: "rearRightDoorOpen", value: obs.doors.rearRightDoorOpen, displayed: true)
    sendEvent(name: "rearLeftDoorOpen", value: obs.doors.rearLeftDoorOpen, displayed: true)
    sendEvent(name: "frontRightDoorOpen", value: obs.doors.frontRightDoorOpen, displayed: true)
    sendEvent(name: "frontLeftDoorOpen", value: obs.doors.frontLeftDoorOpen, displayed: true)
    sendEvent(name: "hoodOpen", value: obs.doors.hoodOpen, displayed: true)
    def running = obs.engineRunning || obs.ERS.status.contains("on")
    sendEvent(name: "engineRunning", value: running, displayed: true)
    sendEvent(name: "switch", value: running ? "on" : "off", displayed: true)
    sendEvent(name: "fuelAmount", value: obs.fuelAmount, displayed: true)
    sendEvent(name: "fuelAmountLevel", value: obs.fuelAmountLevel, displayed: true)
    sendEvent(name: "battery", value: obs.fuelAmountLevel, unit: "%", displayed: true)

    if (obs.hvBattery) {
        sendEvent(name: "hvBatteryChargeStatusDerived", value: obs.hvBattery.hvBatteryChargeStatusDerived, displayed: true)
        sendEvent(name: "hvBatteryChargeModeStatus", value: obs.hvBattery.hvBatteryChargeModeStatus, displayed: true)
        sendEvent(name: "hvBatteryChargeStatus", value: obs.hvBattery.hvBatteryChargeStatus, displayed: true)
        sendEvent(name: "hvBatteryLevel", value: obs.hvBattery.hvBatteryLevel, displayed: true)
        sendEvent(name: "distanceToHVBatteryEmpty", value: obs.hvBattery.distanceToHVBatteryEmpty, displayed: true)
        sendEvent(name: "hvBatteryChargeWarning", value: obs.hvBattery.hvBatteryChargeWarning, displayed: true)
        sendEvent(name: "timeToHVBatteryFullyCharged", value: obs.hvBattery.timeToHVBatteryFullyCharged, displayed: true)
    }

    sendEvent(name: "odometer", value: obs.odometer, displayed: true)
    sendEvent(name: "remoteClimatizationStatus", value: obs.remoteClimatizationStatus, displayed: true)
    sendEvent(name: "serviceWarningStatus", value: obs.serviceWarningStatus, displayed: true)
    sendEvent(name: "theftAlarm", value: obs.theftAlarm, displayed: true)
    sendEvent(name: "tripMeter1", value: obs.tripMeter1, displayed: true)
    sendEvent(name: "tripMeter2", value: obs.tripMeter2, displayed: true)
    sendEvent(name: "frontLeftTyrePressure", value: obs.tyrePressure.frontLeftTyrePressure, displayed: true)
    sendEvent(name: "frontRightTyrePressure", value: obs.tyrePressure.frontRightTyrePressure, displayed: true)
    sendEvent(name: "rearLeftTyrePressure", value: obs.tyrePressure.rearLeftTyrePressure, displayed: true)
    sendEvent(name: "rearRightTyrePressure", value: obs.tyrePressure.rearRightTyrePressure, displayed: true)
    sendEvent(name: "washerFluidLevel", value: obs.washerFluidLevel, displayed: true)
    sendEvent(name: "frontLeftWindowOpen", value: obs.windows.frontLeftWindowOpen, displayed: true)
    sendEvent(name: "frontRightWindowOpen", value: obs.windows.frontRightWindowOpen, displayed: true)
    sendEvent(name: "rearLeftWindowOpen", value: obs.windows.rearLeftWindowOpen, displayed: true)
    sendEvent(name: "rearRightWindowOpen", value: obs.windows.rearRightWindowOpen, displayed: true)

    return
}

private getPosition() {
    def obs = [:]
    def params = getURL("position?client_longitude=-00.00&client_precision=5.000000&client_latitude=00.000")

    log.info "${device.displayName} getting position"
    try {
        httpGet(params) {
            resp ->
                if (resp?.data) obs << resp.data;
                else log.error "http call for position api did not return data: $resp";
        }
    } catch (e) {
        log.error "httpGet call failed for VOC api: $e"
    }

    if (!obs) {
        log.warn "No response from VOC API"
        return
    }

    log obs

    sendEvent(name: "longitude", value: obs.position.longitude, displayed: true)
    sendEvent(name: "latitude", value: obs.position.latitude, displayed: true)
    sendEvent(name: "speed", value: obs.position.speed, displayed: true)
    sendEvent(name: "heading", value: obs.position.heading, displayed: true)

    def double distance = getDistance((double) obs.position.latitude,
        (double) obs.position.longitude,
        (double) location.getLatitude(),
        (double) location.getLongitude(),
        (char)
        "K")

    log "distance = ${distance} presence = ${device.currentValue('presence')}"

    if (distance < 0.05 && device.currentValue('presence') != "present") {
        log "presence detected"
        sendEvent(name: "presence", value: "present", displayed: true)
    }
    if (distance >= 0.05 && device.currentValue('presence') != "not present") {
        log "presence not detected"
        sendEvent(name: "presence", value: "not present", displayed: true)
    }

    def sendThis = "${obs.position.latitude}, ${obs.position.longitude}"
    def sendThis2 = "<a href=\"https://www.google.com/maps/search/?api=1&query=${obs.position.latitude},${obs.position.longitude}\" target=\"_blank\">${obs.position.latitude},${obs.position.longitude}</a>"
    sendEvent(name: "positions", value: sendThis, displayed: true)
    sendEvent(name: "GMaps Link", value: sendThis2, displayed: true)

    return obs
}

private getURI(service) {

    if (service.contains("http")) {
        return service
    }
    def uri = "https://vocapi.wirelesscar.net/customerapi/rest/v3.0/vehicles/${vin}/${service}"
    if (region != null) {
        uri = "https://vocapi-${region}.wirelesscar.net/customerapi/rest/v3.0/vehicles/${vin}/${service}";
    }
    return uri
}

private getURL(service) {

    def params = [
        uri: getURI(service),
        headers: ["authorization": authHeader(),
            "X-Device-Id": "Device",
            "X-OS-Type": "Android",
            "X-Originator-Type": "App",
            "X-OS-Version": "22",
            "Content-Type": "application/json"
        ]
    ]

    return params
}

private authHeader() {
    return "Basic " + (userName + ":" + password).bytes.encodeBase64()
}

private double getDistance(double lat1, double lon1, double lat2, double lon2, char unit) {
    double theta = lon1 - lon2;
    double dist = Math.sin(deg2rad(lat1)) * Math.sin(deg2rad(lat2)) + Math.cos(deg2rad(lat1)) * Math.cos(deg2rad(lat2)) * Math.cos(deg2rad(theta));

    dist = Math.acos(dist);
    dist = rad2deg(dist);
    dist = dist * 60 * 1.1515;
    if (unit == 'K') {
        dist = dist * 1.609344;
    } else if (unit == 'N') {
        dist = dist * 0.8684;
    }
    return (dist);
}

private double deg2rad(double deg) {
    return (deg * Math.PI / 180.0);
}

private double rad2deg(double rad) {
    return (rad * 180.0 / Math.PI);
}