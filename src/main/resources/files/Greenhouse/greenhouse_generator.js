var latitude = 'PUT_LATITUDE';
var longitude = 'PUT_LONGITUDE';
var appId = 'PUT_API_ID';

var msg = { };
var metadata = {
    'latitude' : latitude,
    'longitude' : longitude,
    'appId': appId
};
var msgType = "POST_TELEMETRY_REQUEST";

return {
    msg: msg,
    metadata: metadata,
    msgType: msgType
};