package com.vaguehope.toadcast;

import org.kohsuke.args4j.Option;

public class Args {

	@Option(name = "-d", aliases = { "--daemon" }, usage = "detach form terminal and run in bakground.") private boolean daemonise;
	@Option(name = "-c", aliases = { "--chromecast" }, required = true, usage = "Hostname or IP address of chromecast.") private String chromecast;

	public boolean isDaemonise () {
		return this.daemonise;
	}

	public String getChromecast () {
		return this.chromecast;
	}

}
