# Remote Screen Sharing and Monitoring System

This project is a personal-use remote screen sharing and monitoring system.

## Project Structure
- `app/`: Android application (Streamer)
- `backend/`: Node.js signaling server
- `dashboard/`: React web dashboard (Receiver)

## Setup Instructions

### 1. Signaling Server
1. Navigate to `backend/`
2. Install dependencies: `npm install`
3. Start the server: `node server.js`
4. By default, it runs on `http://localhost:3000`.

### 2. Web Dashboard
1. Navigate to `dashboard/`
2. Install dependencies: `npm install`
3. Update `SIGNALING_SERVER` in `src/App.js` if necessary.
4. Start the dashboard: `npm start`

### 3. Android App
1. Open the project in Android Studio.
2. Update the signaling server URL in `ScreenSharingService.kt`.
3. Build and run the app on your Android phone.
4. Grant the following permissions when prompted:
   - **Usage Access**: Required to detect the currently opened app.
   - **Notification**: Required for the foreground service.
   - **Record Audio**: Required for mic/audio sharing.
5. Click **"Start Remote Sharing"** and accept the screen capture prompt.

## Remote Access (Over Internet)
To use this over the internet:
1. Deploy the `backend` to a service like **Render** or **Heroku**.
2. Update the URLs in the Android app and React dashboard to point to your deployed server.
3. For reliable connectivity across different networks, you should configure a **TURN server**.
   - You can use **Coturn** on a VPS or a managed service like **Twilio Network Traversal Service**.
   - Add your TURN server credentials to `WebRtcManager.kt` and `App.js`.

## Deployment to Render (Backend)
1. Create a new Web Service on Render.
2. Connect your GitHub repository.
3. Set the build command: `npm install`
4. Set the start command: `node server.js`
5. Render will provide a public URL (e.g., `https://your-app.onrender.com`).

## Automatic Updates (GitHub Actions)
The project is configured to automatically build and release the app.
1. To trigger a new release, tag your commit and push it:
   ```bash
   git tag v1.1
   git push origin v1.1
   ```
2. GitHub Actions will:
   - Build the release APK.
   - Create a new GitHub Release with the APK attached.
   - Update `backend/public/version.json` with the new version info and download link.
3. Your Android app will then detect the update when the user clicks **"Check for Updates"**.
