/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.app;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import com.android.internal.R;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.widget.GridLayoutManager;
import com.android.internal.widget.PagerAdapter;
import com.android.internal.widget.RecyclerView;

/**
 * A {@link PagerAdapter} which describes the work and personal profile share sheet screens.
 */
@VisibleForTesting
public class ChooserMultiProfilePagerAdapter extends AbstractMultiProfilePagerAdapter {
    private static final int SINGLE_CELL_SPAN_SIZE = 1;

    private final ChooserProfileDescriptor[] mItems;

    ChooserMultiProfilePagerAdapter(Context context,
            ChooserActivity.ChooserGridAdapter adapter) {
        super(context, /* currentPage */ 0);
        mItems = new ChooserProfileDescriptor[] {
                createProfileDescriptor(adapter)
        };
    }

    ChooserMultiProfilePagerAdapter(Context context,
            ChooserActivity.ChooserGridAdapter personalAdapter,
            ChooserActivity.ChooserGridAdapter workAdapter,
            @Profile int defaultProfile) {
        super(context, /* currentPage */ defaultProfile);
        mItems = new ChooserProfileDescriptor[] {
                createProfileDescriptor(personalAdapter),
                createProfileDescriptor(workAdapter)
        };
    }

    private ChooserProfileDescriptor createProfileDescriptor(
            ChooserActivity.ChooserGridAdapter adapter) {
        final LayoutInflater inflater = LayoutInflater.from(getContext());
        final ViewGroup rootView =
                (ViewGroup) inflater.inflate(R.layout.chooser_list_per_profile, null, false);
        return new ChooserProfileDescriptor(rootView, adapter);
    }

    RecyclerView getListViewForIndex(int index) {
        return getItem(index).recyclerView;
    }

    @Override
    ChooserProfileDescriptor getItem(int pageIndex) {
        return mItems[pageIndex];
    }

    @Override
    int getItemCount() {
        return mItems.length;
    }

    @Override
    ChooserActivity.ChooserGridAdapter getAdapterForIndex(int pageIndex) {
        return mItems[pageIndex].chooserGridAdapter;
    }

    @Override
    void setupListAdapter(int pageIndex) {
        final RecyclerView recyclerView = getItem(pageIndex).recyclerView;
        ChooserActivity.ChooserGridAdapter chooserGridAdapter =
                getItem(pageIndex).chooserGridAdapter;
        recyclerView.setAdapter(chooserGridAdapter);
        GridLayoutManager glm = (GridLayoutManager) recyclerView.getLayoutManager();
        glm.setSpanCount(chooserGridAdapter.getMaxTargetsPerRow());
        glm.setSpanSizeLookup(
                new GridLayoutManager.SpanSizeLookup() {
                    @Override
                    public int getSpanSize(int position) {
                        return chooserGridAdapter.shouldCellSpan(position)
                                ? SINGLE_CELL_SPAN_SIZE
                                : glm.getSpanCount();
                    }
                });
    }

    @Override
    @VisibleForTesting
    public ChooserListAdapter getActiveListAdapter() {
        return getAdapterForIndex(getCurrentPage()).getListAdapter();
    }

    @Override
    @VisibleForTesting
    public ChooserListAdapter getInactiveListAdapter() {
        if (getCount() == 1) {
            return null;
        }
        return getAdapterForIndex(1 - getCurrentPage()).getListAdapter();
    }

    @Override
    ChooserActivity.ChooserGridAdapter getCurrentRootAdapter() {
        return getAdapterForIndex(getCurrentPage());
    }

    @Override
    RecyclerView getCurrentAdapterView() {
        return getListViewForIndex(getCurrentPage());
    }

    class ChooserProfileDescriptor extends ProfileDescriptor {
        private ChooserActivity.ChooserGridAdapter chooserGridAdapter;
        private RecyclerView recyclerView;
        ChooserProfileDescriptor(ViewGroup rootView, ChooserActivity.ChooserGridAdapter adapter) {
            super(rootView);
            chooserGridAdapter = adapter;
            recyclerView = rootView.findViewById(R.id.resolver_list);
        }
    }
}