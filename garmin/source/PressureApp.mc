import Toybox.Activity;
import Toybox.Application;
import Toybox.Communications;
import Toybox.Graphics;
import Toybox.Lang;
import Toybox.Sensor;
import Toybox.SensorHistory;
import Toybox.WatchUi;

// Sends the watch's station pressure (the barometer's own reading, not reduced to sea level)
// to the Ballistics phone app. The phone opens this app and asks; the app answers each request
// and also sends once on start and whenever START is pressed.
class PressureApp extends Application.AppBase {
    var pressurePa = null;
    var status = :none;

    function initialize() {
        AppBase.initialize();
    }

    function onStart(state) {
        Communications.registerForPhoneAppMessages(method(:onPhoneMessage));
        Sensor.enableSensorEvents(method(:onSensor));
        send();
    }

    function onStop(state) {
        Sensor.enableSensorEvents(null);
    }

    function getInitialView() {
        return [new PressureView(self), new PressureDelegate(self)];
    }

    function onSensor(info) {
        pressurePa = read();
        WatchUi.requestUpdate();
    }

    function onPhoneMessage(msg) {
        send();
    }

    // Smoothed ambient pressure first, then the raw sensor, then the newest history sample.
    function read() {
        var activity = Activity.getActivityInfo();
        if (activity != null && activity.ambientPressure != null) {
            return activity.ambientPressure;
        }
        var info = Sensor.getInfo();
        if (info != null && info.pressure != null) {
            return info.pressure;
        }
        var history = SensorHistory.getPressureHistory({ :period => 1, :order => SensorHistory.ORDER_NEWEST_FIRST });
        if (history != null) {
            var sample = history.next();
            if (sample != null && sample.data != null) {
                return sample.data;
            }
        }
        return null;
    }

    function send() {
        pressurePa = read();
        if (pressurePa == null) {
            status = :none;
        } else {
            status = :sending;
            Communications.transmit({ "pa" => pressurePa.toFloat() }, null, new SendListener(self));
        }
        WatchUi.requestUpdate();
    }

    function onSent(ok) {
        status = ok ? :sent : :failed;
        WatchUi.requestUpdate();
    }
}

class SendListener extends Communications.ConnectionListener {
    var app;

    function initialize(owner) {
        ConnectionListener.initialize();
        app = owner;
    }

    function onComplete() {
        app.onSent(true);
    }

    function onError() {
        app.onSent(false);
    }
}

class PressureDelegate extends WatchUi.BehaviorDelegate {
    var app;

    function initialize(owner) {
        BehaviorDelegate.initialize();
        app = owner;
    }

    function onSelect() {
        app.send();
        return true;
    }
}

class PressureView extends WatchUi.View {
    var app;

    function initialize(owner) {
        View.initialize();
        app = owner;
    }

    function onUpdate(dc) {
        var w = dc.getWidth();
        var h = dc.getHeight();
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_BLACK);
        dc.clear();

        var pa = app.pressurePa;
        if (pa == null) {
            dc.drawText(w / 2, h / 2, Graphics.FONT_MEDIUM, (WatchUi.loadResource(Rez.Strings.NoReading) as String),
                Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
            return;
        }
        dc.drawText(w / 2, h * 0.38, Graphics.FONT_NUMBER_MEDIUM, (pa / 100.0).format("%.1f"),
            Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
        dc.drawText(w / 2, h * 0.56, Graphics.FONT_SMALL, "hPa", Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
        dc.setColor(Graphics.COLOR_LT_GRAY, Graphics.COLOR_TRANSPARENT);
        dc.drawText(w / 2, h * 0.66, Graphics.FONT_XTINY, (WatchUi.loadResource(Rez.Strings.Station) as String),
            Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);

        var line = null;
        if (app.status == :sending) {
            line = Rez.Strings.Sending;
        } else if (app.status == :sent) {
            line = Rez.Strings.Sent;
        } else if (app.status == :failed) {
            line = Rez.Strings.NotSent;
        }
        if (line != null) {
            dc.setColor(app.status == :failed ? Graphics.COLOR_ORANGE : Graphics.COLOR_GREEN, Graphics.COLOR_TRANSPARENT);
            dc.drawText(w / 2, h * 0.76, Graphics.FONT_XTINY, (WatchUi.loadResource(line) as String),
                Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
        }
        dc.setColor(Graphics.COLOR_LT_GRAY, Graphics.COLOR_TRANSPARENT);
        dc.drawText(w / 2, h * 0.86, Graphics.FONT_XTINY, (WatchUi.loadResource(Rez.Strings.Hint) as String),
            Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
    }
}
