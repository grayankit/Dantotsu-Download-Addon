# Dantotsu Download Addon

An Android addon for download support in Dantotsu.

## GitHub Actions Release Automation

This project has an automated release workflow configured via GitHub Actions. Whenever a new version tag is pushed, a workflow is triggered to build the release APKs and publish them automatically as a GitHub Release.

### How to Trigger a Release

To release a new version of the app, push a Git tag matching the pattern `v*` (e.g., `v1.1.5`):

```bash
# 1. Add a tag locally
git tag v1.1.5

# 2. Push the tag to GitHub
git push origin v1.1.5
```

### GitHub Repository Setup

To ensure the release workflow succeeds, you must configure the following settings in your GitHub repository:

#### 1. Workflow Permissions
The workflow needs permission to create GitHub Releases and upload APK assets.
1. Go to your repository on GitHub.
2. Navigate to **Settings** > **Actions** > **General**.
3. Under **Workflow permissions**, select **Read and write permissions**.
4. Click **Save**.

#### 2. Release Signing Secrets (Optional)
By default, if no secrets are configured, the release build will fall back to signing the release APKs using the standard `debug` signing configuration (which does not require secrets). This is useful for simple deployment testing.

To sign your release APKs with your production signing credentials, configure the following secrets under **Settings** > **Secrets and variables** > **Actions**:

| Secret Name | Description |
|---|---|
| `KEYSTORE_BASE64` | The Base64-encoded representation of your production keystore file (`.jks` or `.keystore`). |
| `KEYSTORE_PASSWORD` | The password for the keystore. |
| `KEY_ALIAS` | The alias for your release key. |
| `KEY_PASSWORD` | The password for the release key. |

##### How to generate the `KEYSTORE_BASE64` value:
You can encode your keystore to Base64 using the terminal:

```bash
# macOS/Linux
base64 -i your-release-keystore.jks | pbcopy   # Or redirect to a file: base64 -i your-release-keystore.jks > keystore_base64.txt

# Windows (PowerShell)
[Convert]::ToBase64String([IO.File]::ReadAllBytes("your-release-keystore.jks")) | clip
```
Copy the Base64 output and save it as the `KEYSTORE_BASE64` secret in GitHub.
