package com.vaguehope.toadcast;

import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.fourthline.cling.UpnpService;
import org.fourthline.cling.model.types.UDN;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import su.litvak.chromecast.api.v2.ChromeCast;

import com.sun.akuma.Daemon;
import com.vaguehope.toadcast.transcode.Transcoder;
import com.vaguehope.toadcast.util.LogHelper;
import com.vaguehope.toadcast.util.NetHelper;

public class Main {

	private static final Logger LOG = LoggerFactory.getLogger(Main.class);

	private Main () {
		throw new AssertionError();
	}

	public static void main (final String[] rawArgs) throws Exception {// NOSONAR
		LogHelper.bridgeJul();

		final PrintStream err = System.err;
		final Args args = new Args();
		final CmdLineParser parser = new CmdLineParser(args);
		try {
			parser.parseArgument(rawArgs);
			daemonise(args);
			start(args);
			new CountDownLatch(1).await();
			System.exit(0);
		}
		catch (final CmdLineException e) {
			err.println(e.getMessage());
			help(parser, err);
			return;
		}
		catch (final Exception e) {
			err.println("An unhandled error occured.");
			e.printStackTrace(err);
			System.exit(1);
		}
	}

	private static void help (final CmdLineParser parser, final PrintStream ps) {
		ps.print("Usage: ");
		ps.print(C.APPNAME);
		parser.printSingleLineUsage(ps);
		ps.println();
		parser.printUsage(ps);
		ps.println();
	}

	private static void daemonise (final Args args) throws Exception {
		final Daemon d = new Daemon.WithoutChdir();
		if (d.isDaemonized()) {
			d.init(null);// No PID file for now.
		}
		else if (args.isDaemonise()) {
			d.daemonize();
			LOG.info("Daemon started.");
			System.exit(0);
		}
	}

	private static void start (final Args args) throws Exception {// NOSONAR
		final ExecutorService caEs = Executors.newCachedThreadPool();
		final ScheduledExecutorService schEs = Executors.newScheduledThreadPool(1);

		final InetAddress bindAddress = findInterface(args);
		final String hostName = findHostName(bindAddress);

		final String friendlyName = args.getDisplayName(String.format(
				"%s \"%s\" (%s)",
				C.METADATA_MODEL_NAME, args.getChromecast(), hostName));

		final UDN usi = UDN.uniqueSystemIdentifier("ToadCast-ChromeCastRenderer-" + args.getChromecast());
		LOG.info("uniqueSystemIdentifier: {}", usi);

		final UpnpService upnpService = Upnp.makeUpnpServer();

		final Transcoder transcoder = args.isAudio() ? new Transcoder(bindAddress) : null;

		final ChromeCastHolder holder = new ChromeCastHolder();
		scheduleShutdownDisconnect(holder);
		final CastFinder castFinder = new CastFinder(bindAddress, args.getChromecast(), holder, upnpService);

		final GoalSeeker goalSeeker = new GoalSeeker(holder, castFinder, transcoder);
		caEs.execute(goalSeeker);
		upnpService.getRegistry().addDevice(UpnpRenderer.makeMediaRendererDevice(friendlyName, usi, goalSeeker, schEs));

		castFinder.start();
		startReseacher(schEs, upnpService);
	}

	private static InetAddress findInterface (final Args args) throws UnknownHostException, SocketException {
		final InetAddress iface;
		if (args.getInterface() != null) {
			iface = InetAddress.getByName(args.getInterface());
			LOG.info("using interface: {}", iface);
		}
		else {
			final List<InetAddress> addresses = NetHelper.getIpAddresses();
			iface = addresses.iterator().next();
			LOG.info("interfaces: {}, using interface: {}", addresses, iface);
		}
		return iface;
	}

	private static String findHostName (final InetAddress iface) throws UnknownHostException {
		String hostName = iface.getHostName();
		if (hostName.contains(iface.getHostAddress())) hostName = InetAddress.getLocalHost().getHostName();
		LOG.info("hostName: {}", hostName);
		return hostName;
	}

	private static void scheduleShutdownDisconnect (final ChromeCastHolder holder) {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run () {
				final ChromeCast c = holder.getAndSet(null);
				if (c != null) {
					try {
						CastHelper.tidyChromeCast(c);
						c.disconnect();
					}
					catch (final IOException e) {
						LOG.warn("Failed to disconnect ChromeCast.", e);
					}
				}
			}
		});
	}

	private static void startReseacher (final ScheduledExecutorService schEs, final UpnpService upnpService) {
		schEs.scheduleWithFixedDelay(new Runnable() {
			@Override
			public void run () {
				upnpService.getControlPoint().search();
			}
		}, 0, C.UPNP_SEARCH_INTERVAL_SECONDS, TimeUnit.SECONDS);
	}

}
