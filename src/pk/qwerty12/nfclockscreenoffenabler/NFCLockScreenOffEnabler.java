package pk.qwerty12.nfclockscreenoffenabler;

import android.app.AndroidAppHelper;
import android.content.Context;
import android.content.SharedPreferences;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import de.robv.android.xposed.XC_MethodHook;

public class NFCLockScreenOffEnabler implements IXposedHookZygoteInit, IXposedHookLoadPackage
{

	//Thanks to Tungstwenty for the preferences code, which I have taken from his Keyboard42DictInjector and made a bad job of it
	private static final String MY_PACKAGE_NAME = NFCLockScreenOffEnabler.class.getPackage().getName();

	public static final String PREFS = "NFCModSettings";
	public static final String PREF_LOCKED = "On_Locked";

	private SharedPreferences prefs;

	/* -- */
	private static final String PACKAGE_NFC = "com.android.nfc";

	// Taken from NfcService.java, Copyright (C) 2010 The Android Open Source Project, Licensed under the Apache License, Version 2.0
	// Screen state, used by mScreenState
	//private static final int SCREEN_STATE_OFF = 1;
	private static final int SCREEN_STATE_ON_LOCKED = 2;
	private static final int SCREEN_STATE_ON_UNLOCKED = 3;
	/* -- */

	// Thanks to rovo89 for his suggested improvements: http://forum.xda-developers.com/showpost.php?p=35790508&postcount=185
	@Override
	public void initZygote(StartupParam startupParam) throws Throwable
	{
		prefs = AndroidAppHelper.getSharedPreferencesForPackage(MY_PACKAGE_NAME, PREFS, Context.MODE_PRIVATE);
	}

	@Override
	public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable
	{
		if (lpparam.packageName.equals(PACKAGE_NFC))
		{
			try
			{
				XposedHelpers.findAndHookMethod(PACKAGE_NFC + ".NfcService", lpparam.classLoader, "applyRouting", boolean.class,
					new XC_MethodHook()
					{
						@Override
						protected void beforeHookedMethod(MethodHookParam param) throws Throwable
						{
							//Change to preference only takes effect when this is called here
							AndroidAppHelper.reloadSharedPreferencesIfNeeded(prefs);

							final int currScreenState = (Integer) XposedHelpers.callMethod(param.thisObject, "checkScreenState");
							//We also don't need to run if the screen is already on, or if the user has chosen to enable NFC on the lockscreen only and the phone is not locked
							if ((currScreenState == SCREEN_STATE_ON_UNLOCKED) || (prefs.getBoolean(PREF_LOCKED, true) && currScreenState != SCREEN_STATE_ON_LOCKED))
							{
								XposedHelpers.setAdditionalInstanceField(param.thisObject, "mOrigScreenState", -1);
								return;
							}

							synchronized (param.thisObject)   //Not sure if this is correct, but NfcService.java insists on having accesses to the mScreenState variable synchronized, so I'm doing the same here
							{
								XposedHelpers.setAdditionalInstanceField(param.thisObject, "mOrigScreenState", XposedHelpers.getIntField(param.thisObject, "mScreenState"));
								XposedHelpers.setIntField(param.thisObject, "mScreenState", SCREEN_STATE_ON_UNLOCKED);
							}
						}

						@Override
						protected void afterHookedMethod(MethodHookParam param) throws Throwable
						{
							final int mOrigScreenState = (Integer) XposedHelpers.getAdditionalInstanceField(param.thisObject, "mOrigScreenState");
							if (mOrigScreenState == -1)
								return;

							synchronized (param.thisObject)
							{
								//Restore original mScreenState value after applyRouting has run
								XposedHelpers.setIntField(param.thisObject, "mScreenState", mOrigScreenState);
							}
						}

				    });

				/* XposedHelpers.findAndHookMethod(PACKAGE_NFC + ".NfcService$ApplyRoutingTask", lpparam.classLoader, "doInBackground", Integer[].class,
						new XC_MethodHook()
						{
							@Override
							protected void beforeHookedMethod(MethodHookParam param) throws Throwable
							{
								AndroidAppHelper.reloadSharedPreferencesIfNeeded(prefs);

								if (!prefs.getBoolean(PREF_LOCKED, true))
								{
									final int currScreenState = (Integer) XposedHelpers.callMethod(XposedHelpers.getSurroundingThis(param.thisObject), "checkScreenState");
									if (currScreenState != SCREEN_STATE_OFF)
										if (param.args.length == 1)
											param.args[0] = new Integer[] {SCREEN_STATE_OFF};
								}
							}

							@Override
							protected void afterHookedMethod(MethodHookParam param) throws Throwable
							{
							}

					    }); */

			}
			catch (Throwable t) { XposedBridge.log(t); }
		}
	}

}
