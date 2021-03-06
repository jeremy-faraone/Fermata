package me.aap.fermata.ui.fragment;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.google.android.play.core.install.InstallException;

import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.R;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Folders;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.pref.FoldersPrefs;
import me.aap.fermata.media.service.FermataServiceUiBinder;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.view.MediaItemWrapper;
import me.aap.fermata.util.Utils;
import me.aap.fermata.vfs.FermataVfsManager;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.log.Log;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.fragment.FilePickerFragment;
import me.aap.utils.ui.menu.OverlayMenu;
import me.aap.utils.ui.menu.OverlayMenuItem;
import me.aap.utils.vfs.VirtualFileSystem;
import me.aap.utils.vfs.VirtualFolder;
import me.aap.utils.vfs.VirtualResource;
import me.aap.utils.vfs.local.LocalFileSystem;

import static android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION;
import static java.util.Objects.requireNonNull;
import static me.aap.fermata.vfs.FermataVfsManager.GDRIVE_ID;
import static me.aap.fermata.vfs.FermataVfsManager.SFTP_ID;
import static me.aap.fermata.vfs.FermataVfsManager.SMB_ID;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.collection.CollectionUtils.filterMap;
import static me.aap.utils.function.ResultConsumer.Cancel.isCancellation;

/**
 * @author Andrey Pavlenko
 */
public class FoldersFragment extends MediaLibFragment {

	@Override
	ListAdapter createAdapter(FermataServiceUiBinder b) {
		return new FoldersAdapter(getMainActivity(), b.getLib().getFolders());
	}

	@Override
	public int getFragmentId() {
		return R.id.folders_fragment;
	}

	@Override
	public CharSequence getFragmentTitle() {
		return getResources().getString(R.string.folders);
	}

	@Override
	public void contributeToNavBarMenu(OverlayMenu.Builder builder) {
		if (isRootFolder()) {
			builder.addItem(R.id.folders_add, R.drawable.add_folder, R.string.add_folder)
					.setHandler(this::navBarMenuItemSelected);
		} else {
			FoldersAdapter a = getAdapter();
			OverlayMenu.Builder b = builder.withSelectionHandler(this::navBarMenuItemSelected);
			b.addItem(R.id.refresh, R.drawable.refresh, R.string.refresh);
			b.addItem(R.id.rescan, R.drawable.loading, R.string.rescan);

			if (!a.hasSelectable()) return;

			if (a.getListView().isSelectionActive()) {
				boolean hasSelected = a.hasSelected();

				b.addItem(R.id.nav_select_all, R.drawable.check_box, R.string.select_all);
				b.addItem(R.id.nav_unselect_all, R.drawable.check_box_blank, R.string.unselect_all);

				if (hasSelected) {
					b.addItem(R.id.favorites_add, R.drawable.favorite, R.string.favorites_add);
					getMainActivity().addPlaylistMenu(b, completed(a.getSelectedItems()));
				}
			} else {
				b.addItem(R.id.nav_select, R.drawable.check_box, R.string.select);
			}
		}

		super.contributeToNavBarMenu(builder);
	}

	private boolean navBarMenuItemSelected(OverlayMenuItem item) {
		switch (item.getItemId()) {
			case R.id.folders_add:
				addFolder();
				return true;
			case R.id.nav_select:
			case R.id.nav_select_all:
				getAdapter().getListView().select(true);
				return true;
			case R.id.nav_unselect_all:
				getAdapter().getListView().select(false);
				return true;
			case R.id.favorites_add:
				requireNonNull(getLib()).getFavorites().addItems(filterMap(getAdapter().getList(),
						MediaItemWrapper::isSelected, (i, w, l) -> l.add((PlayableItem) w.getItem()),
						ArrayList::new));
				discardSelection();
				MediaLibFragment f = getMainActivity().getMediaLibFragment(R.id.favorites_fragment);
				if (f != null) f.reload();
				return true;
			case R.id.refresh:
				refresh();
				return true;
			case R.id.rescan:
				rescan();
				return true;
		}

		return false;
	}

	public void navBarItemReselected(int itemId) {
		getAdapter().setParent(getLib().getFolders());
	}

	@Override
	public void onHiddenChanged(boolean hidden) {
		super.onHiddenChanged(hidden);
		if (hidden) return;

		FoldersAdapter a = getAdapter();
		if (a != null) a.animateAddButton(a.getParent());
	}

	@Override
	public void switchingTo(@NonNull ActivityFragment newFragment) {
		super.switchingTo(newFragment);
		getMainActivity().getFloatingButton().clearAnimation();
	}

	public void addFolder() {
		if (getMainActivity().isCarActivity()) {
			addFolderPicker();
			return;
		}

		OverlayMenu menu = getMainActivity().getContextMenu();
		menu.show(b -> {
			b.setTitle(R.string.add_folder);
			b.setSelectionHandler(this::addFolder);
			b.addItem(R.id.vfs_content, R.string.vfs_content);
			b.addItem(R.id.vfs_file_system, R.string.vfs_file_system);
			b.addItem(R.id.vfs_sftp, R.string.vfs_sftp);
			b.addItem(R.id.vfs_smb, R.string.vfs_smb);
			b.addItem(R.id.vfs_gdrive, R.string.vfs_gdrive);
		});
	}

	private boolean addFolder(OverlayMenuItem item) {
		switch (item.getItemId()) {
			case R.id.vfs_content:
				addFolderIntent();
				return true;
			case R.id.vfs_file_system:
				addFolderPicker();
				return true;
			case R.id.vfs_gdrive:
				addFolderVfs(GDRIVE_ID, R.string.vfs_gdrive);
				return true;
			case R.id.vfs_sftp:
				addFolderVfs(SFTP_ID, R.string.vfs_sftp);
				return true;
			case R.id.vfs_smb:
				addFolderVfs(SMB_ID, R.string.vfs_smb);
				return true;
			default:
				return false;
		}
	}

	private void addFolderIntent() {
		try {
			Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
			getMainActivity().startActivityForResult(intent).onSuccess(this::addFolderResult);
		} catch (ActivityNotFoundException ex) {
			String msg = ex.getLocalizedMessage();
			Utils.showAlert(getContext(), getString(R.string.err_failed_add_folder,
					(msg != null) ? msg : ex.toString()));
		}
	}

	public void addFolderPicker() {
		addFolderPicker(LocalFileSystem.getInstance());
	}

	private void addFolderPicker(VirtualFileSystem fs) {
		FilePickerFragment f = getMainActivity().showFragment(R.id.file_picker);
		f.setMode(FilePickerFragment.FOLDER);
		f.setFileSystem(fs);
		f.setFileConsumer(this::addFolderResult);
	}

	private void addFolderVfs(String provId, @StringRes int name) {
		FermataVfsManager mgr = getLib().getVfsManager();
		mgr.getProvider(provId)
				.then(p -> p.select(getMainActivity(), mgr.getFileSystems(provId)))
				.main()
				.onFailure(fail -> failedToLoadModule(name, fail))
				.onSuccess(this::addFolderResult);
	}

	private void failedToLoadModule(@StringRes int name, Throwable ex) {
		getMainActivity().showFragment(R.id.folders_fragment);
		if (isCancellation(ex)) return;

		App.get().getHandler().post(() -> {
			String n = getString(name);
			Log.e(ex, "Failed to load add folder: ", name);

			if (ex instanceof InstallException) {
				Utils.showAlert(getContext(), getString(R.string.err_failed_install_module, n));
			} else {
				String msg = ex.getLocalizedMessage();
				Utils.showAlert(getContext(), getString(R.string.err_failed_add_folder,
						(msg != null) ? msg : ex.toString()));
			}
		});
	}

	private void addFolderResult(Intent data) {
		if (data == null) return;

		Uri uri = data.getData();
		if (uri == null) return;

		MainActivityDelegate a = getMainActivity();
		requireNonNull(a.getContext()).getContentResolver()
				.takePersistableUriPermission(uri, FLAG_GRANT_READ_URI_PERMISSION);
		Folders folders = getLib().getFolders();
		folders.addItem(uri).main().thenRun(() -> getAdapter().setParent(folders));
	}

	private void addFolderResult(VirtualResource folder) {
		MainActivityDelegate a = getMainActivity();
		if (folder instanceof VirtualFolder) {
			Folders folders = a.getLib().getFolders();
			folders.addItem(folder.getRid().toAndroidUri()).main()
					.thenRun(() -> getAdapter().setParent(folders));
		}
		a.showFragment(getFragmentId());
	}

	@Override
	protected boolean isSupportedItem(Item i) {
		return getFolders().isFoldersItemId(i.getId());
	}

	private Folders getFolders() {
		return getLib().getFolders();
	}

	private boolean isRootFolder() {
		BrowsableItem p = getAdapter().getParent();
		return (p == null) || (p instanceof Folders);
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, List<PreferenceStore.Pref<?>> prefs) {
		FoldersAdapter a = getAdapter();
		if (a.isCallbackCall()) return;
		if (prefs.contains(FoldersPrefs.FOLDERS) && isRootFolder()) a.reload();
		else super.onPreferenceChanged(store, prefs);
	}

	private final class FoldersAdapter extends ListAdapter {

		FoldersAdapter(MainActivityDelegate activity, BrowsableItem parent) {
			super(activity, parent);
			animateAddButton(parent);
		}

		@Override
		public FutureSupplier<?> setParent(BrowsableItem parent, boolean userAction) {
			return super.setParent(parent, userAction).onSuccess(v -> animateAddButton(parent));
		}

		public boolean isLongPressDragEnabled() {
			return isRootFolder();
		}

		public boolean isItemViewSwipeEnabled() {
			return isRootFolder();
		}

		@Override
		protected void onItemDismiss(int position) {
			BrowsableItem i = getAdapter().getParent();
			if (i instanceof Folders) ((Folders) i).removeItem(position);
			super.onItemDismiss(position);
		}

		@Override
		protected boolean onItemMove(int fromPosition, int toPosition) {
			BrowsableItem i = getAdapter().getParent();
			if (i instanceof Folders) ((Folders) i).moveItem(fromPosition, toPosition);
			return super.onItemMove(fromPosition, toPosition);
		}

		private void animateAddButton(BrowsableItem parent) {
			if (!(parent instanceof Folders)) return;

			parent.getUnsortedChildren().onSuccess(c -> {
				if (!c.isEmpty()) return;

				Animation shake = AnimationUtils.loadAnimation(getContext(), me.aap.utils.R.anim.shake_y_20);
				getMainActivity().getFloatingButton().startAnimation(shake);
			});
		}
	}
}
