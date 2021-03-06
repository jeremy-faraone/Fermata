package me.aap.fermata.ui.fragment;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.view.View;

import androidx.core.text.HtmlCompat;

import com.google.android.material.textview.MaterialTextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.FermataApplication;
import me.aap.fermata.R;
import me.aap.fermata.addon.AddonInfo;
import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.addon.FermataAddon;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.util.Utils;
import me.aap.utils.collection.CollectionUtils;
import me.aap.utils.function.Supplier;
import me.aap.utils.log.Log;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceStore.Compound;
import me.aap.utils.pref.PreferenceStore.Pref;
import me.aap.utils.text.SharedTextBuilder;
import me.aap.utils.ui.UiUtils;
import me.aap.utils.ui.activity.ActivityDelegate;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.fragment.GenericFragment;
import me.aap.utils.ui.menu.OverlayMenu;
import me.aap.utils.ui.menu.OverlayMenuItem;
import me.aap.utils.ui.view.NavBarItem;
import me.aap.utils.ui.view.NavBarView;
import me.aap.utils.ui.view.NavButtonView;
import me.aap.utils.ui.view.PrefNavBarMediator;

import static android.content.res.Configuration.ORIENTATION_PORTRAIT;
import static me.aap.fermata.BuildConfig.VERSION_CODE;
import static me.aap.fermata.BuildConfig.VERSION_NAME;
import static me.aap.utils.ui.UiUtils.ID_NULL;
import static me.aap.utils.ui.UiUtils.createAlertDialog;

/**
 * @author Andrey Pavlenko
 */
public class NavBarMediator extends PrefNavBarMediator implements AddonManager.Listener,
		OverlayMenu.SelectionHandler {
	public static final NavBarMediator instance = new NavBarMediator();

	private NavBarMediator() {
	}

	@Override
	public void enable(NavBarView nb, ActivityFragment f) {
		super.enable(nb, f);
		FermataApplication.get().getAddonManager().addBroadcastListener(this);
	}

	@Override
	public void disable(NavBarView nb) {
		super.disable(nb);
		FermataApplication.get().getAddonManager().removeBroadcastListener(this);
	}

	@Override
	public void addonChanged(AddonManager mgr, AddonInfo info, boolean installed) {
		NavBarView nb = navBar;
		if (nb != null) reload(nb);
	}

	@Override
	protected PreferenceStore getPreferenceStore(NavBarView nb) {
		return MainActivityDelegate.get(nb.getContext()).getPrefs();
	}

	@Override
	protected Pref<Compound<List<NavBarItem>>> getPref(NavBarView nb) {
		return new NavBarPref(nb.getContext());
	}

	@Override
	public void itemSelected(View item, int id, ActivityDelegate a) {
		if (id == R.id.menu) {
			showMenu(MainActivityDelegate.get(item.getContext()));
		} else {
			super.itemSelected(item, id, a);
		}
	}

	@Override
	protected boolean extItemSelected(OverlayMenuItem item) {
		if (item.getItemId() == R.id.menu) {
			NavButtonView.Ext ext = getExtButton();

			if ((ext != null) && !ext.isSelected()) {
				NavBarItem i = item.getData();
				setExtButton(null, i);
			}

			showMenu(MainActivityDelegate.get(item.getContext()));
			return true;
		} else {
			return super.extItemSelected(item);
		}
	}

	@Override
	public void showMenu(NavBarView nb) {
		showMenu(MainActivityDelegate.get(nb.getContext()));
	}

	public void showMenu(MainActivityDelegate a) {
		OverlayMenu menu = a.findViewById(R.id.nav_menu_view);
		menu.show(b -> {
			b.setSelectionHandler(this);

			if (a.hasCurrent())
				b.addItem(R.id.nav_got_to_current, R.drawable.go_to_current, R.string.got_to_current);

			ActivityFragment f = a.getActiveFragment();
			if (f instanceof MainActivityFragment) ((MainActivityFragment) f).contributeToNavBarMenu(b);

			b.addItem(R.id.nav_about, R.drawable.about, R.string.about);
			b.addItem(R.id.settings_fragment, R.drawable.settings, R.string.settings);
			if (!a.isCarActivity()) b.addItem(R.id.nav_exit, R.drawable.exit, R.string.exit);

			if (BuildConfig.AUTO) b.addItem(R.id.nav_donate, R.drawable.coffee, R.string.donate);
		});
	}

	@Override
	public boolean menuItemSelected(OverlayMenuItem item) {
		switch (item.getItemId()) {
			case R.id.nav_got_to_current:
				MainActivityDelegate.get(item.getContext()).goToCurrent();
				return true;
			case R.id.nav_about:
				MainActivityDelegate a = MainActivityDelegate.get(item.getContext());
				GenericFragment f = a.showFragment(R.id.generic_fragment);
				f.setTitle(item.getContext().getString(R.string.about));
				f.setContentProvider(g -> {
					Context ctx = g.getContext();
					MaterialTextView v = new MaterialTextView(ctx);
					String url = "https://github.com/AndreyPavlenko/Fermata";
					String openUrl = url + "/blob/master/README.md#donation";
					String html = ctx.getString(R.string.about_html, openUrl, url, VERSION_NAME, VERSION_CODE);
					int pad = UiUtils.toIntPx(ctx, 10);
					v.setPadding(pad, pad, pad, pad);
					v.setText(HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY));
					if (!a.isCarActivity()) v.setOnClickListener(t -> openUrl(t.getContext(), openUrl));
					g.addView(v);
				});
				return true;
			case R.id.settings_fragment:
				MainActivityDelegate.get(item.getContext()).showFragment(R.id.settings_fragment);
				return true;
			case R.id.nav_exit:
				MainActivityDelegate.get(item.getContext()).finish();
				return true;
			default:
				if (BuildConfig.AUTO && (item.getItemId() == R.id.nav_donate)) {
					Context ctx = item.getContext();
					a = MainActivityDelegate.get(ctx);

					if (a.isCarActivity()) {
						Utils.showAlert(ctx, R.string.use_phone_for_donation);
						return true;
					}

					DialogInterface.OnClickListener ok = (d, i) -> {
						int[] selection = new int[]{0};
						String[] wallets = new String[]{"CloudTips", "PayPal", "Yandex"};
						String[] urls = new String[]{
								"https://pay.cloudtips.ru/p/a03a73da",
								"https://paypal.me/AndrewPavlenko",
								"https://money.yandex.ru/to/410014661137336"
						};

						createAlertDialog(ctx)
								.setSingleChoiceItems(wallets, 0, (dlg, which) -> selection[0] = which)
								.setNegativeButton(android.R.string.cancel, null)
								.setPositiveButton(android.R.string.ok, (d1, w1) -> openUrl(ctx, urls[selection[0]]))
								.show();
					};

					createAlertDialog(ctx)
							.setIcon(R.drawable.coffee)
							.setTitle(R.string.donate)
							.setMessage(R.string.donate_text)
							.setNegativeButton(android.R.string.cancel, null)
							.setPositiveButton(android.R.string.ok, ok).show();

					return true;
				}

				return false;
		}
	}

	private static void openUrl(Context ctx, String url) {
		Uri u = Uri.parse(url);
		Intent intent = new Intent(Intent.ACTION_VIEW, u);

		try {
			MainActivityDelegate.get(ctx).startActivity(intent);
		} catch (ActivityNotFoundException ex) {
			String msg = ctx.getResources().getString(R.string.err_failed_open_url, u);
			createAlertDialog(ctx).setMessage(msg)
					.setPositiveButton(android.R.string.ok, null).show();
		}
	}

	private static final class NavBarPref implements Pref<Compound<List<NavBarItem>>>, Compound<List<NavBarItem>> {
		private static final Pref<Supplier<String>> prefV = Pref.s("NAV_BAR_V", () -> null);
		private static final Pref<Supplier<String>> prefH = Pref.s("NAV_BAR_H", () -> null);
		private final Context ctx;

		public NavBarPref(Context ctx) {
			this.ctx = ctx;
		}

		@Override
		public String getName() {
			return "NAV_BAR";
		}

		@Override
		public Compound<List<NavBarItem>> getDefaultValue() {
			return this;
		}

		@Override
		public List<NavBarItem> get(PreferenceStore store, String name) {
			AddonManager amgr = FermataApplication.get().getAddonManager();
			List<NavBarItem> items = new ArrayList<>(BuildConfig.ADDONS.length + 4);
			Pref<Supplier<String>> pref = getPref();
			int max = (pref == prefV) ? 4 : 6;
			String v = store.getStringPref(pref);
			if (v == null) v = store.getStringPref((pref == prefH) ? prefV : prefH);

			if (v != null) {
				for (String s : v.split(",")) {
					int idx = s.indexOf('_');

					if ((idx == -1) || (idx == s.length() - 1)) {
						Log.w("Invalid value of NAV_BAR pref: " + v);
						break;
					}

					boolean pin = s.startsWith("true_");
					s = s.substring(idx + 1);
					NavBarItem item = getItem(amgr, s, pin);
					if (item != null) items.add(item);
				}
			}

			if (!CollectionUtils.contains(items, i -> i.getId() == R.id.folders_fragment)) {
				items.add(NavBarItem.create(ctx, R.id.folders_fragment, R.drawable.folder, R.string.folders, true));
			}
			if (!CollectionUtils.contains(items, i -> i.getId() == R.id.favorites_fragment)) {
				items.add(NavBarItem.create(ctx, R.id.favorites_fragment, R.drawable.favorite_filled, R.string.favorites, true));
			}
			if (!CollectionUtils.contains(items, i -> i.getId() == R.id.playlists_fragment)) {
				items.add(NavBarItem.create(ctx, R.id.playlists_fragment, R.drawable.playlist, R.string.playlists, true));
			}

			for (AddonInfo ai : BuildConfig.ADDONS) {
				FermataAddon a = amgr.getAddon(ai.className);
				if ((a != null) && (a.getNavId() != ID_NULL) &&
						!CollectionUtils.contains(items, i -> i.getId() == a.getNavId())) {
					items.add(NavBarItem.create(ctx, a.getNavId(), ai.icon, ai.addonName, items.size() < max));
				}
			}

			if (!CollectionUtils.contains(items, i -> i.getId() == R.id.menu)) {
				items.add(NavBarItem.create(ctx, R.id.menu, R.drawable.menu, R.string.menu, false));
			}

			return items;
		}

		@Override
		public void set(PreferenceStore.Edit edit, String name, List<NavBarItem> value) {
			AddonManager amgr = FermataApplication.get().getAddonManager();
			String v;

			try (SharedTextBuilder tb = SharedTextBuilder.get()) {
				for (NavBarItem i : value) {
					String itemName = getName(amgr, i);

					if (itemName == null) {
						Log.w("Nav bar item name not found for " + i.getText());
					} else {
						if (tb.length() > 0) tb.append(',');
						tb.append(i.isPinned()).append('_').append(itemName);
					}
				}

				v = tb.toString();
			}

			edit.setStringPref(getPref(), v);
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			return Objects.equals(getName(), ((NavBarPref) o).getName());
		}

		@Override
		public int hashCode() {
			return getName().hashCode();
		}

		private Pref<Supplier<String>> getPref() {
			return (ctx.getResources().getConfiguration().orientation == ORIENTATION_PORTRAIT) ? prefV : prefH;
		}

		private static String getName(AddonManager amgr, NavBarItem i) {
			int id = i.getId();

			switch (id) {
				case R.id.folders_fragment:
					return "folders";
				case R.id.favorites_fragment:
					return "favorites";
				case R.id.playlists_fragment:
					return "playlists";
				case R.id.menu:
					return "menu";
				default:
					for (AddonInfo ai : BuildConfig.ADDONS) {
						FermataAddon a = amgr.getAddon(ai.className);
						if ((a != null) && (a.getNavId() == id)) return ai.className;
					}

					return null;
			}
		}

		private NavBarItem getItem(AddonManager amgr, String name, boolean pin) {
			switch (name) {
				case "folders":
					return NavBarItem.create(ctx, R.id.folders_fragment, R.drawable.folder, R.string.folders, pin);
				case "favorites":
					return NavBarItem.create(ctx, R.id.favorites_fragment, R.drawable.favorite_filled, R.string.favorites, pin);
				case "playlists":
					return NavBarItem.create(ctx, R.id.playlists_fragment, R.drawable.playlist, R.string.playlists, pin);
				case "menu":
					return NavBarItem.create(ctx, R.id.menu, R.drawable.menu, R.string.menu, pin);
				default:
					for (AddonInfo ai : BuildConfig.ADDONS) {
						if (name.equals(ai.className)) {
							FermataAddon a = amgr.getAddon(ai.className);
							if ((a != null) && (a.getNavId() != ID_NULL)) {
								return NavBarItem.create(ctx, a.getNavId(), ai.icon, ai.addonName, pin);
							}
						}
					}

					return null;
			}
		}
	}
}
