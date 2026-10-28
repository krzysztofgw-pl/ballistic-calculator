import Toybox.Activity;
import Toybox.Application;
import Toybox.Communications;
import Toybox.Graphics;
import Toybox.Lang;
import Toybox.Sensor;
import Toybox.SensorHistory;
import Toybox.System;
import Toybox.Timer;
import Toybox.WatchUi;

// Sends the watch's station pressure (the barometer's own reading, not reduced to sea level)
// to the Ballistics phone app. The phone opens this app and asks; the app answers each request
// and also sends once on start and whenever START is pressed. It runs only while open and closes
// itself a few seconds after a reading reaches the phone, so it costs no battery in the background.
class PressureApp extends Application.AppBase {
    var pressurePa as Float or Null = null;
    var status as Symbol = :none;
    var closeTimer as Timer.Timer = new Timer.Timer();

    function initialize() {
        AppBase.initialize();
    }

    function onStart(state as Dictionary or Null) as Void {
        Communications.registerForPhoneAppMessages(method(:onPhoneMessage));
        Sensor.enableSensorEvents(method(:onSensor));
        send();
    }

    function onStop(state as Dictionary or Null) as Void {
        closeTimer.stop();
        Sensor.enableSensorEvents(null);
    }

    function getInitialView() {
        return [new PressureView(self), new PressureDelegate(self)];
    }

    function onSensor(info as Sensor.Info) as Void {
        pressurePa = read();
        WatchUi.requestUpdate();
    }

    function onPhoneMessage(msg as Communications.PhoneAppMessage) as Void {
        send();
    }

    // Smoothed ambient pressure first, then the raw sensor, then the newest history sample.
    function read() as Float or Null {
        var activity = Activity.getActivityInfo();
        if (activity != null && activity.ambientPressure != null) {
            return activity.ambientPressure;
        }
        var info = Sensor.getInfo();
        if (info.pressure != null) {
            return info.pressure;
        }
        var history = SensorHistory.getPressureHistory({ :period => 1, :order => SensorHistory.ORDER_NEWEST_FIRST });
        var sample = history.next();
        if (sample != null) {
            var data = sample.data;
            if (data != null) {
                return data.toFloat();
            }
        }
        return null;
    }

    function send() as Void {
        var pa = read();
        pressurePa = pa;
        if (pa == null) {
            status = :none;
        } else {
            closeTimer.stop();
            status = :sending;
            Communications.transmit({ "pa" => pa }, null, new SendListener(self));
        }
        WatchUi.requestUpdate();
    }

    function onSent(ok as Boolean) as Void {
        status = ok ? :sent : :failed;
        if (ok) {
            closeTimer.start(method(:close), 5000, false);
        }
        WatchUi.requestUpdate();
    }

    function close() as Void {
        System.exit();
    }
}

class SendListener extends Communications.ConnectionListener {
    var app as PressureApp;

    function initialize(owner as PressureApp) {
        ConnectionListener.initialize();
        app = owner;
    }

    function onComplete() as Void {
        app.onSent(true);
    }

    function onError() as Void {
        app.onSent(false);
    }
}

class PressureDelegate extends WatchUi.BehaviorDelegate {
    var app as PressureApp;

    function initialize(owner as PressureApp) {
        BehaviorDelegate.initialize();
        app = owner;
    }

    function onSelect() as Boolean {
        app.send();
        return true;
    }
}

class PressureView extends WatchUi.View {
    var app as PressureApp;

    function initialize(owner as PressureApp) {
        View.initialize();
        app = owner;
    }

    function text(id as ResourceId) as String {
        return WatchUi.loadResource(id) as String;
    }

    function onUpdate(dc as Graphics.Dc) as Void {
        var w = dc.getWidth();
        var h = dc.getHeight();
        var center = Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER;
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_BLACK);
        dc.clear();

        var pa = app.pressurePa;
        if (pa == null) {
            dc.drawText(w / 2, h / 2, Graphics.FONT_MEDIUM, text(Rez.Strings.NoReading), center);
            return;
        }
        dc.drawText(w / 2, h * 0.38, Graphics.FONT_NUMBER_MEDIUM, (pa / 100.0).format("%.1f"), center);
        dc.drawText(w / 2, h * 0.56, Graphics.FONT_SMALL, "hPa", center);
        dc.setColor(Graphics.COLOR_LT_GRAY, Graphics.COLOR_TRANSPARENT);
        dc.drawText(w / 2, h * 0.66, Graphics.FONT_XTINY, text(Rez.Strings.Station), center);

        var status = app.status;
        if (status == :sending) {
            dc.setColor(Graphics.COLOR_GREEN, Graphics.COLOR_TRANSPARENT);
            dc.drawText(w / 2, h * 0.76, Graphics.FONT_XTINY, text(Rez.Strings.Sending), center);
        } else if (status == :sent) {
            dc.setColor(Graphics.COLOR_GREEN, Graphics.COLOR_TRANSPARENT);
            dc.drawText(w / 2, h * 0.76, Graphics.FONT_XTINY, text(Rez.Strings.Sent), center);
        } else if (status == :failed) {
            dc.setColor(Graphics.COLOR_ORANGE, Graphics.COLOR_TRANSPARENT);
            dc.drawText(w / 2, h * 0.76, Graphics.FONT_XTINY, text(Rez.Strings.NotSent), center);
        }
        dc.setColor(Graphics.COLOR_LT_GRAY, Graphics.COLOR_TRANSPARENT);
        dc.drawText(w / 2, h * 0.86, Graphics.FONT_XTINY, text(Rez.Strings.Hint), center);
    }
}
