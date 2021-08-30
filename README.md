# Telegram bot for tracking of orders
This is the bot I'm curating for my own purposes to track packages.
However, I do not mind any PRs or forks.

# Roadmap
* Fixing bugs
* Add more providers
* Improve support for auto-detection of track numbers
* Extend support for tracking providers
* Dockerize
* Spring Native-ize app
# Supported Tracking Providers
* Russian Post
* Russian Samsung Store
* Qwintry
* Courier Service Expert

# Hows and whats:
* WebCheckerBot.java is the core logic for the bot, all messages and states are handled there. It supports two layer state: main commands list and sub-command list if user hasn't finished the job. I.e. after adding a new track number, the user may be required to add additional info. If so, the bot expects the next message to be such.
* TrackingParser.java is an abstract class for the core logic of tracking provider. Any support for future tracking provider should be extended from it since the scheduler calls child's update function.
* Entities contain all necessary constants, db entities and enums to handle user state and info.
* Bot needs minimal user information to function: user id, track number & email (for now, since other providers may require phone number or what else).
* Scheduling is set-up to check hourly, could be tweaked to your liking.
* The checking of tracking works with support of headless Selenium Firefox driver.
* Once delivered, any tracking info is deleted.
* In order to work, requires env vars:
  * JDBC_URL - jdbc url for the db's table;
  * JDBC_USERNAME - username for the table;
  * JDBC_PASSWORD - password for the username of table;
  * BOT_TOKEN - telegram bot's token, which is given after creating one with BotFather;
  * BOT_NAME - bot's name for the telegram-bots API.
