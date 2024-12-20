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


var consumer_consumption_by_hour_workday_gov = function (hour) {
    switch (hour) {
        case 0:
        case 1:
        case 2:
        case 3:
        case 4:
        case 5:
        case 6:
            return 0;
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
            return 50;
        case 21:
        case 22:
        case 23:
            return 0;
        default:
            return Number.NaN;
    }
}

var consumer_consumption_by_hour_weekend_gov = function (hour) {
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
            return 0;
        default:
            return Number.NaN;
    }
}

var consumer_consumption_by_hour_gov = function (hour, dayOfWeek) {
    if (dayOfWeek === 6 || dayOfWeek === 7) {
        return consumer_consumption_by_hour_weekend_gov(hour);
    }
    return consumer_consumption_by_hour_workday_gov(hour);
}


var consumer_consumption_gov = function () {
    var currentDate = new Date();
    currentDate.setMinutes(0, 0, 0);

    var ts = currentDate.getTime();
    var hour = currentDate.getHours();
    var dayOfWeek = currentDate.getDay(); // 1-7
    var dayOfYear = getDayOfYear(currentDate);

    var dailyNoiseAmplitude = 60;

    var timezoneShift = 2;
    hour = (hour + timezoneShift + 24) % 24;

    var consumption = 0;
    consumption += consumer_consumption_by_hour_gov(hour, dayOfWeek);
    consumption += getRandomInt(-dailyNoiseAmplitude, dailyNoiseAmplitude);

    var value = Math.max(0, consumption);
    return {
        ts: ts,
        values: {
            consumption: value
        }
    }
}

var consumption = consumer_consumption_gov();

var msg = consumption;
var metadata = {};
var msgType = "POST_TELEMETRY_REQUEST";
return {msg: msg, metadata: metadata, msgType: msgType};