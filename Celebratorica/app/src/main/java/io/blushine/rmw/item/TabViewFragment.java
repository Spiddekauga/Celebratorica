package io.blushine.rmw.item;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.TabLayout;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import com.squareup.otto.Subscribe;

import io.blushine.android.AppFragment;
import io.blushine.rmw.R;
import io.blushine.rmw.util.AppActivity;
import io.blushine.utils.EventBus;

/**
 * Main fragment for showing all tabs and their fragments
 */
public class TabViewFragment extends AppFragment {
private static final EventBus mEventBus = EventBus.getInstance();
private static final String PAGE_POSITION_KEY = "page_position";
private ViewPager mCategoryViewPager;
private CategoryPagerAdapter mCategoryAdapter;
private TabLayout mTabLayout;
private FloatingActionButton mAddButton;
private int mPositionAfterUpdate = -1;

@Override
public void onCreate(Bundle savedInstanceState) {
	super.onCreate(savedInstanceState);
	mEventBus.register(this);
}

@Override
public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
	inflater.inflate(R.menu.menu_edit_categories, menu);
	inflater.inflate(R.menu.menu_default, menu);
}

@Nullable
@Override
public View onCreateViewImpl(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
	View view = inflater.inflate(R.layout.fragment_item_view, container, false);
	
	Toolbar toolbar = view.findViewById(R.id.toolbar);
	AppActivity.getActivity().setSupportActionBar(toolbar);
	setHasOptionsMenu(true);
	
	mAddButton = view.findViewById(R.id.add_button);
	mAddButton.setOnClickListener(v -> {
		// Current category id
		Category category = getSelectedCategory();
		
		if (category != null && !category.getId().isEmpty()) {
			ItemAddFragment itemAddFragment = new ItemAddFragment();
			itemAddFragment.setCategoryId(category.getId());
			itemAddFragment.show();
		}
	});
	
	ImageButton addCategoryButton = view.findViewById(R.id.add_category_button);
	addCategoryButton.setOnClickListener(v -> new CategoryAddFragment().show());
	
	if (mCategoryViewPager != null) {
		mPositionAfterUpdate = mCategoryViewPager.getCurrentItem();
	}
	
	mCategoryViewPager = view.findViewById(R.id.view_pager);
	mTabLayout = view.findViewById(R.id.view_pager_tabs);
	mTabLayout.setupWithViewPager(mCategoryViewPager);
	bindAdapter();
	
	return view;
}

private Category getSelectedCategory() {
	int currentItemIndex = mCategoryViewPager.getCurrentItem();
	return mCategoryAdapter.getCategory(currentItemIndex);
}

private void bindAdapter() {
	if (mCategoryViewPager != null && mCategoryViewPager.getAdapter() == null) {
		if (mCategoryAdapter == null) {
			mCategoryAdapter = new CategoryPagerAdapter(getChildFragmentManager());
		}
		mCategoryViewPager.setAdapter(mCategoryAdapter);
		updateLongPressListeners();
		
		// Hide add button
		if (mCategoryAdapter.getCount() == 0) {
			mAddButton.setVisibility(View.GONE);
		}
		ItemRepo.getInstance().getCategories();
//		displayShowcases();
	}
}

private void updateLongPressListeners() {
	LinearLayout slidingTabStrip = (LinearLayout) mTabLayout.getChildAt(0);
	for (int i = 0; i < slidingTabStrip.getChildCount(); i++) {
		final int position = i;
		View tabView = slidingTabStrip.getChildAt(position);
		tabView.setLongClickable(true);
		tabView.setOnLongClickListener(view -> {
			Category category = mCategoryAdapter.getCategory(position);
			
			if (category != null && !category.getId().isEmpty()) {
				CategoryEditFragment categoryEditFragment = new CategoryEditFragment();
				categoryEditFragment.setArguments(category);
				categoryEditFragment.show();
			}
			
			return true;
		});
	}
}

//private void displayShowcases() {
//	if (ItemRepo.getInstance().isBackendInitialized()) {
//		Category category = getSelectedCategory();
//		if (mAddButton.getVisibility() == View.GONE) {
//			Showcases.ADD_FIRST_CATEGORY.show(mAddCategoryButton);
//		} else if (category != null && !activeCategoryHasItems()) {
//			Showcases.ADD_ITEM.show(mAddButton);
//		} else {
//			Showcases.ADD_ANOTHER_CATEGORY.show(mAddCategoryButton);
//		}
//	}
//}

//private boolean activeCategoryHasItems() {
//	Category category = getSelectedCategory();
//	return category != null && !ItemRepo.getInstance().getItems(category.getId()).isEmpty();
//}

@Override
public void onViewStateRestored(Bundle savedInstanceState) {
	super.onViewStateRestored(savedInstanceState);
	if (mPositionAfterUpdate != -1) {
		mCategoryViewPager.setCurrentItem(mPositionAfterUpdate, false);
		mPositionAfterUpdate = -1;
	} else if (savedInstanceState != null) {
		mCategoryViewPager.setCurrentItem(savedInstanceState.getInt(PAGE_POSITION_KEY, 0));
	}
}

@Override
public void onResume() {
	super.onResume();
//	displayShowcases();
}

@Override
public void onSaveInstanceState(Bundle outState) {
	super.onSaveInstanceState(outState);
	outState.putInt(PAGE_POSITION_KEY, mCategoryViewPager.getCurrentItem());
}

@Override
public void onDestroy() {
	super.onDestroy();
	mEventBus.unregister(this);
}

@SuppressWarnings("unused")
@Subscribe
public void onCategory(CategoryEvent event) {
	// Update tabs
	if (mCategoryAdapter != null && event.getFirstObject() != null) {
		Category category = event.getFirstObject();
		Category selectedCategory = getSelectedCategory();
		
		switch (event.getAction()) {
		case ADDED:
			// Added first category -> Show add item button
			if (mCategoryAdapter.getCount() == 0) {
				mAddButton.setVisibility(View.VISIBLE);
				mPositionAfterUpdate = 1;
			}
			// Adjust position if we added a category before the selected (can happen when we restore a deleted category)
			else if (category.getOrder() <= selectedCategory.getOrder()) {
				mPositionAfterUpdate += 1;
			}
			
			mCategoryAdapter.addItem(category);
			break;
		
		case EDITED:
			mPositionAfterUpdate = selectedCategory.getOrder() - 1;
			mCategoryAdapter.sortItems();
			break;
		
		case REMOVED:
			mCategoryAdapter.removeItem(category);
			
			// Removed the last category -> Hide add item button
			if (mCategoryAdapter.getCount() == 0) {
				mAddButton.setVisibility(View.GONE);
			}
			// Adjust position if we removed an item before the selected item
			else if (category.getOrder() < selectedCategory.getOrder()) {
				mPositionAfterUpdate -= 1;
			}
			break;
		
		case GET_RESPONSE:
			mCategoryAdapter.setItems(event.getObjects());
			break;
		}
		
		mCategoryViewPager.setCurrentItem(mPositionAfterUpdate, false);
		updateLongPressListeners();
	}
}
}
