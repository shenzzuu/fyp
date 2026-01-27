# PlanPrep – Personalized Meal Planning App

**PlanPrep** is a mobile application designed to simplify meal planning for busy individuals, such as students and working professionals. By focusing on simplicity and personalization, the app helps users reduce decision fatigue, manage their time efficiently, and maintain healthy eating habits.

Developed as a **Final Year Project** for the Bachelor of Information Technology (Hons.) at Universiti Teknologi MARA (UiTM).

## 🚀 Key Features

* **Personalized Onboarding**: Tailors meal suggestions based on user lifestyle and dietary restrictions (allergies).
* **Structured Scheduling**: Organize Breakfast, Lunch, Dinner, and Supper with a simple daily/weekly planner.
* **Quick Actions**: Features like "Copy Yesterday," "Surprise Me" (random suggestions), and "Clear Day" to save time.
* **Grocery List Manager**: Automatically generates shopping lists based on your planned meals.
* **Meal Tracking**: An "I Have Eaten" button to help users stay accountable and enjoy the tracking process.
* **Smart Reminders**: Integrated notifications to ensure you never miss a prepared meal.

## 🛠️ Tech Stack

* **Frontend**: Android Studio (Meerkat Version).
* **Backend**: Firebase (Firestore for real-time database, Firebase Authentication for security).
* **APIs**:
    * **Apify API**: For web scraping and food data retrieval.
    * **ImgBB API**: For efficient image handling and visualization.
    * **Google Maps Scraper API**: For location-based features.

## 📥 Download
You can download the latest version of the app here:

[**Download PlanPrep APK**](https://github.com/shenzzuu/fyp/raw/main/release/PlanPrep.apk)

> *Note: If the direct link doesn't work, verify the latest release in the "Releases" sidebar on the right.*

## 🏗️ Project Architecture

The project follows the **Waterfall Model**, ensuring a structured flow through Requirement Identification, Design & Development, and Testing.

* **Design Philosophy**: User-Centered Design (UCD) to ensure the interface remains clean, intuitive, and clutter-free.
* **Performance**: During User Acceptance Testing (UAT), the app achieved a high Perceived Ease of Use (PEU) mean score of 4.57.

## 🛡️ Security & Setup

To protect sensitive data, API keys are managed through `local.properties` and accessed via `BuildConfig`. This ensures that private tokens (like Apify) are never exposed in the public repository.

**How to run locally:**

1.  Clone the repository.
2.  Create a `local.properties` file in the root directory.
3.  Add your API key: `APIFY_TOKEN="your_key_here"`.
4.  Sync Gradle and run the project.

## 👨‍💻 Author

**Muhammad Hafiz Bin Mohd Rafi**
Supervised by Sir Muhammad Nabil Fikri Bin Jamaluddin.
Bachelor of Information Technology (Hons.)
Faculty of Computer and Mathematical Sciences, UiTM.