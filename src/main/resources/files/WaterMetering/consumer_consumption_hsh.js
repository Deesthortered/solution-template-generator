var getRandomInt = function (min, max) {
    if (min === max) {
        return min;
    }
    min = Math.ceil(min);
    max = Math.floor(max);
    return Math.floor(Math.random() * (max - min + 1)) + min;
}

var getDayOfYear = function (date) {
    var start = new Date(date.getFullYear(), 0, 0);
    var diff = date - start;
    var oneDay = 1000 * 60 * 60 * 24;
    var day = Math.floor(diff / oneDay);
    return day;
}


var consumer_consumption_by_hour_workday_hsh = function (hour) {
    switch (hour) {
        case 0:
        case 1:
        case 2:
        case 3:
        case 4:
        case 5:
        case 6:
        case 7:
        case 8:
        case 9:
        case 10:
        case 11:
        case 12:
        case 13:
        case 14:
        case 15:
        case 16:
        case 17:
        case 18:
        case 19:
        case 20:
        case 21:
        case 22:
        case 23:
            return 100;
        default:
            return Number.NaN;
    }
}

var consumer_consumption_by_hour_weekend_hsh = function (hour) {
    switch (hour) {
        case 0:
        case 1:
        case 2:
        case 3:
        case 4:
        case 5:
        case 6:
        case 7:
        case 8:
        case 9:
        case 10:
        case 11:
        case 12:
        case 13:
        case 14:
        case 15:
        case 16:
        case 17:
        case 18:
        case 19:
        case 20:
        case 21:
        case 22:
        case 23:
            return 100;
        default:
            return Number.NaN;
    }
}

var consumer_consumption_by_hour_hsh = function (hour, dayOfWeek) {
    if (dayOfWeek === 6 || dayOfWeek === 7) {
        return consumer_consumption_by_hour_weekend_hsh(hour);
    }
    return consumer_consumption_by_hour_workday_hsh(hour);
}


var consumer_consumption_hsh = function () {
    var currentDate = new Date();
    currentDate.setMinutes(0, 0, 0);

    var ts = currentDate.getTime();
    var hour = currentDate.getHours();
    var dayOfWeek = currentDate.getDay(); // 1-7
    var dayOfYear = getDayOfYear(currentDate);
    var month = currentDate.getMonth(); // 0-11

    var dailyNoiseAmplitude = 5;

    var timezoneShift = 2;
    hour = (hour + timezoneShift + 24) % 24;

    var value = 0;
    value += consumer_consumption_by_hour_hsh(hour, dayOfWeek);
    value += getRandomInt(-dailyNoiseAmplitude, dailyNoiseAmplitude);

    return {
        ts: ts,
        values: {
            consumption: value
        }
    }
}

var consumption = consumer_consumption_hsh();

var msg = consumption;
var metadata = {};
var msgType = "POST_TELEMETRY_REQUEST";
return {msg: msg, metadata: metadata, msgType: msgType};