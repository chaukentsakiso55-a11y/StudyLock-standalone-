# StudyLock uninstall protection

StudyLock 1.0.3 supports two Android protection levels.

## Standard protection

The user explicitly approves StudyLock as an Android Device Administrator. This creates an uninstall barrier: administrator protection must be removed before StudyLock can be normally uninstalled. StudyLock's own release button requires the saved parent password.

Android and device manufacturers may still allow an administrator to be deactivated from system settings. StudyLock does not attempt to hide or disable Android recovery/settings screens.

## Full managed protection

When Android has provisioned StudyLock as a Device Owner or Profile Owner, StudyLock uses `DevicePolicyManager.setUninstallBlocked(...)` to ask Android itself to block removal of the StudyLock package. The policy is persistent and StudyLock reapplies the desired state when the app resumes or a focus session starts.

Full managed protection is not silently enabled by installation. Android provisioning and consent requirements still apply.

## Recovery

StudyLock requires a parent password before uninstall protection can be enabled. The in-app release action verifies that password before removing the policy. Standard Device Admin mode remains recoverable through Android system settings.
