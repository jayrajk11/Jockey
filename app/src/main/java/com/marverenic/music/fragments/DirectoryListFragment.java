package com.marverenic.music.fragments;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.marverenic.music.BuildConfig;
import com.marverenic.music.JockeyApplication;
import com.marverenic.music.R;
import com.marverenic.music.data.store.PreferencesStore;
import com.marverenic.music.dialog.DirectoryDialogFragment;
import com.marverenic.music.instances.section.BasicEmptyState;
import com.marverenic.music.utils.Themes;
import com.marverenic.music.view.EnhancedAdapters.DragBackgroundDecoration;
import com.marverenic.music.view.EnhancedAdapters.DragDividerDecoration;
import com.marverenic.music.view.EnhancedAdapters.DragDropAdapter;
import com.marverenic.music.view.EnhancedAdapters.EnhancedViewHolder;
import com.marverenic.music.view.EnhancedAdapters.HeterogeneousAdapter;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

public class DirectoryListFragment extends Fragment implements View.OnClickListener,
        DirectoryDialogFragment.OnDirectoryPickListener {

    public static final String PREFERENCE_EXTRA = "EXTRA_PREFERENCE_KEY";
    public static final String TITLE_EXTRA = "FRAGMENT_HEADER";

    private static final String TAG = "DirectoryListFragment";
    private static final String TAG_DIR_DIALOG = "DirectoryListFragment_DirectoryDialog";
    private static final boolean D = BuildConfig.DEBUG;

    @Inject
    PreferencesStore mPreferencesStore;

    private String mPrefKey;
    private String mOppositePrefKey;
    private String mTitle;
    private List<String> mDirectories;
    private Set<String> mOppositeDirectories;
    private DragDropAdapter mAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPrefKey = getArguments().getString(PREFERENCE_EXTRA);
        mTitle = getArguments().getString(TITLE_EXTRA);

        JockeyApplication.getComponent(this).inject(this);

        Set<String> dirs = preferences.getStringSet(mPrefKey, Collections.<String>emptySet());

        if (mPrefKey.equals(Prefs.DIR_INCLUDED)) {
            mOppositePrefKey = Prefs.DIR_EXCLUDED;
        } else if (mPrefKey.equals(Prefs.DIR_EXCLUDED)) {
            mOppositePrefKey = Prefs.DIR_INCLUDED;
        } else if (D) {
            Log.i(TAG, "onCreate: Couldn\'t load opposite set to check "
                    + "inclusion/exclusion conflicts");
        }

        mOppositeDirectories = preferences.getStringSet(
                mOppositePrefKey, Collections.<String>emptySet());

        mDirectories = new ArrayList<>(dirs);

        Fragment directoryPicker = getFragmentManager().findFragmentByTag(TAG_DIR_DIALOG);
        if (directoryPicker instanceof DirectoryDialogFragment) {
            ((DirectoryDialogFragment) directoryPicker).setDirectoryPickListener(this);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        ViewGroup view =
                (ViewGroup) inflater.inflate(R.layout.fragment_directory_list, container, false);
        view.findViewById(R.id.fab).setOnClickListener(this);

        mAdapter = new DragDropAdapter();
        mAdapter.setEmptyState(new BasicEmptyState() {
            @Override
            public String getMessage() {
                if (mPrefKey.equals(Prefs.DIR_INCLUDED)) {
                    return getString(R.string.empty_included_dirs);
                }
                if (mPrefKey.equals(Prefs.DIR_EXCLUDED)) {
                    return getString(R.string.empty_excluded_dirs);
                }
                return super.getMessage();
            }

            @Override
            public String getDetail() {
                if (mPrefKey.equals(Prefs.DIR_INCLUDED)) {
                    return getString(R.string.empty_included_dirs_detail);
                }
                if (mPrefKey.equals(Prefs.DIR_EXCLUDED)) {
                    return getString(R.string.empty_excluded_dirs_detail);
                }
                return super.getMessage();
            }
        });

        mAdapter.addSection(new DirectorySection(mDirectories));

        RecyclerView list = (RecyclerView) view.findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(getContext()));
        mAdapter.attach(list);

        list.addItemDecoration(new DragDividerDecoration(getContext(), R.id.subheaderFrame));

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        Toolbar toolbar = (Toolbar) getActivity().findViewById(R.id.toolbar);
        if (toolbar != null && mTitle != null) {
            toolbar.setTitle(mTitle);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        Prefs.getPrefs(getContext()).edit()
                .putStringSet(mPrefKey, new HashSet<>(mDirectories))
                .putStringSet(mOppositePrefKey, mOppositeDirectories)
                .commit();
    }

    @Override
    public void onClick(View v) {
        new DirectoryDialogFragment()
                .setDirectoryPickListener(this)
                .show(getFragmentManager(), TAG_DIR_DIALOG);
    }

    @Override
    public void onDirectoryChosen(final File directory) {
        if (mOppositeDirectories.contains(directory.getAbsolutePath())) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            AlertDialog.OnClickListener clickListener = (dialog, which) -> {
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    mOppositeDirectories.remove(directory.getAbsolutePath());
                    mDirectories.add(directory.getAbsolutePath());

                    if (mDirectories.size() == 1) {
                        mAdapter.notifyDataSetChanged();
                    } else {
                        mAdapter.notifyItemInserted(mDirectories.size() - 1);
                    }
                }
            };

            if (mPrefKey.equals(Prefs.DIR_INCLUDED)) {
                builder
                        .setMessage(getString(
                                R.string.confirm_dir_include_excluded, directory.getName()))
                        .setPositiveButton(R.string.action_include, clickListener);
            } else {
                builder
                        .setMessage(getString(
                                R.string.confirm_dir_exclude_included, directory.getName()))
                        .setPositiveButton(R.string.action_exclude, clickListener);
            }

            builder
                    .setNegativeButton(R.string.action_cancel, null)
                    .show();
        } else if (mDirectories.contains(directory.getAbsolutePath())) {
            //noinspection ConstantConditions
            Snackbar
                    .make(
                            getView(),
                            getString(
                                    mPrefKey.equals(Prefs.DIR_INCLUDED)
                                            ? R.string.confirm_dir_already_included
                                            : R.string.confirm_dir_already_excluded,
                                    directory.getName()),
                            Snackbar.LENGTH_SHORT)
                    .show();
        } else {
            mDirectories.add(directory.getAbsolutePath());
            if (mDirectories.size() == 1) {
                mAdapter.notifyDataSetChanged();
            } else {
                mAdapter.notifyItemInserted(mDirectories.size() - 1);
            }
        }
    }

    private class DirectorySection extends DragDropAdapter.ListSection<String> {

        private static final int SECTION_ID = 727;

        public DirectorySection(List<String> data) {
            super(SECTION_ID, data);
        }

        @Override
        public EnhancedViewHolder<String> createViewHolder(HeterogeneousAdapter adapter,
                                                           ViewGroup parent) {
            return new DirectoryViewHolder(
                    LayoutInflater.from(parent.getContext())
                            .inflate(R.layout.instance_directory, parent, false));
        }
    }

    private class DirectoryViewHolder extends EnhancedViewHolder<String>
            implements View.OnClickListener, PopupMenu.OnMenuItemClickListener {

        TextView directoryName;
        TextView directoryPath;
        String reference;
        int index;

        /**
         * @param itemView The view that this ViewHolder will manage
         */
        public DirectoryViewHolder(View itemView) {
            super(itemView);

            directoryName = (TextView) itemView.findViewById(R.id.instanceTitle);
            directoryPath = (TextView) itemView.findViewById(R.id.instanceDetail);

            itemView.findViewById(R.id.instanceMore).setOnClickListener(this);
        }

        @SuppressLint("SetTextI18n")
        @Override
        public void update(String item, int position) {
            reference = item;
            index = position;

            directoryPath.setText(item + File.separatorChar);

            int lastSeparatorIndex = item.lastIndexOf(File.separatorChar);
            if (lastSeparatorIndex < 0) {
                directoryName.setText(item);
            } else {
                directoryName.setText(item.substring(lastSeparatorIndex + 1));
            }
        }

        @Override
        public void onClick(View v) {
            PopupMenu menu = new PopupMenu(itemView.getContext(), v, Gravity.END);
            menu.getMenu().add(getString(R.string.action_remove));
            menu.setOnMenuItemClickListener(this);
            menu.show();
        }

        @Override
        public boolean onMenuItemClick(MenuItem item) {
            final String removedReference = reference;
            final int removedIndex = index;

            // Remove this view holder's reference
            mDirectories.remove(reference);
            if (mDirectories.isEmpty()) {
                mAdapter.notifyItemChanged(0);
            } else {
                mAdapter.notifyItemRemoved(index);
            }

            // Prompt a confirmation Snackbar with undo button
            Snackbar
                    .make(itemView, getString(R.string.message_removed_directory, reference),
                            Snackbar.LENGTH_LONG)
                    .setAction(R.string.action_undo,
                            v -> {
                                mDirectories.add(removedIndex, removedReference);
                                if (mDirectories.size() == 1) {
                                    mAdapter.notifyItemChanged(0);
                                } else {
                                    mAdapter.notifyItemRemoved(removedIndex);
                                }
                            })
                    .show();

            return true;
        }
    }
}
