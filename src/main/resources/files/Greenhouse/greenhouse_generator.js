var date = new Date();
date.setMinutes(0, 0, 0);
var ts = date.getTime();

var latitude = 'PUT_LATITUDE';
var longitude = 'PUT_LONGITUDE';
var appId = 'PUT_API_ID';

var msg = { };
var metadata = {
    'latitude' : latitude,
    'longitude' : longitude,
    'appId': appId,
    'ts': ts
};
var msgType = "POST_TELEMETRY_REQUEST";

return {
    msg: msg,
    metadata: metadata,
    msgType: msgType
};