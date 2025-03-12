package atavism.agis.plugins;

import java.io.IOException;
import atavism.server.engine.*;
import atavism.server.util.Log;
import atavism.msgsys.*;

public class WeatherClient {

	private WeatherClient() {
	}

	public static Namespace NAMESPACE = null;

	
	
	public static void SetWorldTime(int year, int month, int day, int hour, int minute) throws IOException {
		SetWorldTimeMessage msg = new SetWorldTimeMessage(year, month, day, hour, minute);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("SocialClient: InviteByNameMessage hit 2");
	}

	public static class SetWorldTimeMessage extends GenericMessage {
		private static final long serialVersionUID = 1L;

		public SetWorldTimeMessage() {
			super();
			Log.debug("WeatherClient: SetWorldTimeMessage hit 1");
		}

		public SetWorldTimeMessage(int year, int month, int day, int hour, int minute) {
			super();
			setMsgType(MSG_TYPE_SET_WORLD_TIME);
			setProperty("year", year);
			setProperty("month", month);
			setProperty("day", day);
			setProperty("hour", hour);
			setProperty("minute", minute);
			Log.debug("WeatherClient: SetWorldTimeMessage hit 1");
		}
	}
	
	public static void SetWeatherProfile(OID ply,int profil) throws IOException {
		SetWeatherProfileMessage msg = new SetWeatherProfileMessage(ply,profil);
		Engine.getAgent().sendBroadcast(msg);
		Log.debug("SocialClient: InviteByNameMessage hit 2");
	}

	public static class SetWeatherProfileMessage extends GenericMessage {
		private static final long serialVersionUID = 1L;

		public SetWeatherProfileMessage() {
			super();
			Log.debug("WeatherClient: SetWorldTimeMessage hit 1");
		}

		public SetWeatherProfileMessage(OID ply, int profil) {
			super();
			setMsgType(MSG_TYPE_SET_WEATHER_PROFILE);
			setProperty("profil", profil);
			setProperty("ply", ply);
			
			Log.debug("WeatherClient: SetWorldTimeMessage hit 1");
		}
	}
	

	public static final MessageType MSG_TYPE_SET_WORLD_TIME = MessageType.intern("weather.SET_WORLD_TIME");
	public static final MessageType MSG_TYPE_SET_WEATHER_PROFILE = MessageType.intern("weather.SET_WEATHER_PROFILE");
	public static final MessageType MSG_TYPE_GET_WEATHER_PROFILE = MessageType.intern("weather.GET_WEATHER_PROFILE");

}
