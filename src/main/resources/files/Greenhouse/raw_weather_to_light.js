function makeLightData(date, clouds) {
    var hour = date.getHours();
    var day = getDayOfYear(date);

    var hourLux = getHourLuxValues(hour);
    var yearLux = getYearLuxCycleValue(day);
    var percents = getPercentByClouds(clouds);
    var noise = getRandomInt(-1000, 1000);
    var value = (hourLux + yearLux) * percents + noise;
    value = Math.max(0, value);

    return value;
}

function getDayOfYear(date) {
    var start = new Date(date.getFullYear(), 0, 0);
    var diff = date - start;
    var oneDay = 1000 * 60 * 60 * 24;
    var day = Math.floor(diff / oneDay);
    return day;
}

function getRandomInt(min, max) {
    if (min === max) {
        return min;
    }
    min = Math.ceil(min);
    max = Math.floor(max);
    return Math.floor(Math.random() * (max - min + 1)) + min;
}

function getPercentByClouds(clouds) {
    return ((100 - clouds) * 0.6 + 40) * 0.01;
}

function getYearLuxCycleValue(day) {
    var diff = 12000;
    if (172 <= day && day < 356) {
        return (-diff * (day - 172)) / (356 - 172);
    } else if (356 <= day) {
        return (diff * (day - 356)) / ((365 - 356) + 172) - diff;
    } else  {
        return (diff * (day + (365 - 356))) / ((365 - 356) + 172) - diff;
    }
}

function getHourLuxValues(hour) {
    /// As summer day [0 - 17_000]
    switch (hour) {
        case 0:
        case 1:
        case 2:
        case 3:
            return 0;
        case 4:
            return 1000;
        case 5:
            return 2000;
        case 6:
            return 4000;
        case 7:
            return 6000;
        case 8:
            return 9000;
        case 9:
            return 12000;
        case 10:
            return 14000;
        case 11:
            return 16000;
        case 12:
        case 13:
            return 17000;
        case 14:
        case 15:
            return 16000;
        case 16:
            return 15000;
        case 17:
            return 13000;
        case 18:
            return 11000;
        case 19:
            return 8000;
        case 20:
            return 6000;
        case 21:
            return 4000;
        case 22:
            return 1000;
        case 23:
            return 0;
        default: Number.NaN;
    }
}


function makeNecessaryData() {
    var date = new Date(parseInt(metadata.ts));
    return makeLightData(date, msg.clouds.all);
}

metadata.values_light_out = makeNecessaryData();

return {msg: msg, metadata: metadata, msgType: msgType};