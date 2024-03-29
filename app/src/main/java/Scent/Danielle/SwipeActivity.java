package Scent.Danielle;

// Android core components
import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Canvas;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

// AndroidX imports
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

// Third-party library for image loading
import com.bumptech.glide.Glide;

// Firebase imports
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

// Custom imports
import Scent.Danielle.Utils.DataModel.Item;
import Scent.Danielle.Utils.DataModel.Swipe;
import Scent.Danielle.Utils.Database.FirebaseInitialization;

// Java standard imports
import java.util.ArrayList;
import java.util.List;

public class SwipeActivity extends Fragment {
    private final String TAG = SwipeActivity.class.getSimpleName();
    private final String SWIPE = "swipe";
    private SwipeItemAdapter itemAdapter;
    private List<Item> itemList;

    // Custom LinearLayoutManager to make RecyclerView non-scrollable
    private final class NonScrollableLayoutManager extends LinearLayoutManager {
        @Override
        public boolean canScrollVertically() {
            return false;
        }

        public NonScrollableLayoutManager() {
            super(requireContext());
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.activity_swipe, container, false);

        // Initialize RecyclerView
        RecyclerView itemRecyclerView = rootView.findViewById(R.id.itemRecyclerView);
        itemRecyclerView.setLayoutManager(new NonScrollableLayoutManager());

        // Initialize item list and adapter
        itemList = new ArrayList<>();
        itemAdapter = new SwipeItemAdapter(itemList);
        itemRecyclerView.setAdapter(itemAdapter);

        // Retrieve and handle swipe items from Firebase
        handleSwipeItemsFromFirebase();

        // Attach swipe functionality to the RecyclerView
        itemAdapter.attachSwipeHelperToRecyclerView(itemRecyclerView);

        return rootView;
    }

    // Retrieves items from Firebase and handles swipe events.
    private void handleSwipeItemsFromFirebase() {
        FirebaseInitialization.getItemsDatabaseReference().addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Item> fetchedItems = new ArrayList<>();

                // Iterate through each item in the Firebase snapshot
                for (DataSnapshot childSnapshot : snapshot.getChildren()) {
                    Item item = childSnapshot.getValue(Item.class);

                    // Skip current user's own items
                    if (item.getUserId().equals(FirebaseInitialization.getCurrentUserId())) {
                        continue;
                    }

                    // Check if the item is swiped by the current user
                    DataSnapshot swipeSnapshot = childSnapshot.child("swipe");
                    if (!isSwipedByCurrentUser(swipeSnapshot, FirebaseInitialization.getCurrentUserId())) {
                        fetchedItems.add(item);
                    }
                }

                // Update UI with fetched items
                handleUIUpdateWithItems(fetchedItems);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Log error if Firebase data retrieval fails
                Log.e(TAG, "Error retrieving items from Firebase: " + error.getMessage());
            }
        });
    }

    // Checks if the item is swiped by the current user.
    private boolean isSwipedByCurrentUser(DataSnapshot swipeSnapshot, String currentUserId) {
        if (!swipeSnapshot.exists()) {
            return false;
        }

        // Iterate through each swipe action
        for (DataSnapshot swipeChildSnapshot : swipeSnapshot.getChildren()) {
            String swipeUserId = swipeChildSnapshot.child("userId").getValue(String.class);
            if (swipeUserId != null && swipeUserId.equals(currentUserId)) {
                return true;
            }
        }

        return false;
    }

    // Handles UI update with fetched items.
    @SuppressLint("NotifyDataSetChanged")
    private void handleUIUpdateWithItems(List<Item> fetchedItems) {
        if (!fetchedItems.isEmpty() && itemAdapter != null) {
            itemList.clear();
            itemList.addAll(fetchedItems);
            itemAdapter.notifyDataSetChanged();
            Log.d(TAG, "Items retrieved and UI updated successfully.");
        } else {
            Log.d(TAG, "No items to display or adapter is unavailable.");
        }
    }

    // Method to handle swipe event and record it in Firebase.
    private void handleSwipeEvent(final String itemKey, final boolean isSwipedRight) {
        // Construct the reference to the swipe action in the Firebase database
        DatabaseReference swipeReference = FirebaseInitialization.getItemsDatabaseReference().child(itemKey).child("swipe");

        // Create a new swipe action reference
        DatabaseReference swipeActionRef = swipeReference.push();

        // Create a Swipe object with relevant information
        String currentUserId = FirebaseInitialization.getCurrentUserId();
        String currentUserDisplayName = FirebaseInitialization.getCurrentUserDisplayName();
        String currentUserDisplayPhotoUrl = FirebaseInitialization.getCurrentUserDisplayPhotoUrl();

        Swipe swipe = new Swipe(currentUserId, currentUserDisplayName, currentUserDisplayPhotoUrl, isSwipedRight);

        // Push the swipe action to the database and handle completion
        swipeActionRef.setValue(swipe).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                // Log a successful swipe action
                Log.d(TAG, "Swipe recorded successfully by: " + currentUserDisplayName);
            } else {
                // Log an error if the swipe action failed to be recorded
                Log.e(TAG, "Failed to record swipe action: " + task.getException().getMessage());
            }
        });
    }

    // Method to initiate a conversation when swiping up on an item.
    private void swipeUp(Item item) {
        // Create an intent to start the ConversationActivity
        Intent intent = new Intent(requireContext(), ConversationActivity.class);

        // Pass necessary data to the ConversationActivity
        intent.putExtra("id", item.getUserId());
        intent.putExtra("name", item.getFullName());
        intent.putExtra("avatar", item.getAvatar());

        // Set the flags for the intent
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        // Start the ConversationActivity
        requireActivity().startActivity(intent);
    }

    // RecyclerView adapter for displaying feed items with swipe functionality.
    private final class SwipeItemAdapter extends RecyclerView.Adapter<SwipeActivity.SwipeItemDisplayHolder> {
        private final List<Item> swipeItemList;
        private static final float SWIPE_THRESHOLD = 0.5f;

        public SwipeItemAdapter(List<Item> swipeItemList) { this.swipeItemList = swipeItemList; }

        @Override
        public int getItemCount() {
            return swipeItemList.isEmpty() ? 0 : 1; // Only one item is displayed at a time
        }

        @NonNull
        @Override
        public SwipeActivity.SwipeItemDisplayHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_swipe, parent, false);
            return new SwipeActivity.SwipeItemDisplayHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull SwipeActivity.SwipeItemDisplayHolder holder, int position) {
            Item currentItem = swipeItemList.get(position);
            holder.bindItem(currentItem);
        }

        // Attaches swipe helper to the RecyclerView.
        public void attachSwipeHelperToRecyclerView(RecyclerView recyclerView) {
            new ItemTouchHelper(simpleCallback).attachToRecyclerView(recyclerView);
        }

        // Implement swipe left and swipe right
        private final ItemTouchHelper.SimpleCallback simpleCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT | ItemTouchHelper.UP | ItemTouchHelper.DOWN) {

            @Override
            public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
                return SWIPE_THRESHOLD;
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
                // Customize the swipe animation
                final View itemView = viewHolder.itemView;
                final float itemHeight = (float) itemView.getHeight();
                final float itemWidth = (float) itemView.getWidth();

                // Calculate the rotation angle based on swipe distance
                float rotation = 0;
                if (dX != 0) {
                    rotation = -dX / itemWidth * 45;
                }

                // Set up the rotation and translation effects
                itemView.setPivotX(itemWidth / 2);
                itemView.setPivotY(itemHeight / 2);
                itemView.setRotation(rotation);
                itemView.setTranslationX(dX);
                itemView.setTranslationY(dY);

                // Apply opacity based on swipe distance
                final float alpha = 1.0f - Math.abs(dX) / itemWidth;
                itemView.setAlpha(alpha);

                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder view, int direction) {
                // Determine swipe direction
                String swipeDirection;
                int position = view.getAdapterPosition();
                Item currentItem = swipeItemList.get(position);

                if (direction == ItemTouchHelper.LEFT) {
                    handleSwipeEvent(currentItem.getKey(), false);
                    swipeDirection = "Left";

                } else if (direction == ItemTouchHelper.RIGHT) {
                    handleSwipeEvent(currentItem.getKey(), true);
                    swipeDirection = "Right";

                } else if (direction == ItemTouchHelper.UP) {
                    swipeUp(currentItem);
                    swipeDirection = "Up";

                } else {
                    swipeDirection = "Down";
                }

                swipeItemList.remove(position);
                notifyItemRemoved(position);

                // Perform actions based on swipe direction
                Toast.makeText(requireActivity(), "Swiped " + swipeDirection, Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Swiped " + swipeDirection);
            }
        };
    }

    // ViewHolder for displaying feed items.
    private class SwipeItemDisplayHolder extends RecyclerView.ViewHolder {
        private final ImageView mediaImageView;
        private final TextView titleTextView;
        private final TextView nameTextView;
        private final TextView descriptionTextView;

        private SwipeItemDisplayHolder(@NonNull View view) {
            super(view);
            mediaImageView = view.findViewById(R.id.mediaImageView);
            titleTextView = view.findViewById(R.id.titleTextView);
            nameTextView = view.findViewById(R.id.nameTextView);
            descriptionTextView = view.findViewById(R.id.descriptionTextView);
            Log.d(TAG, "ViewHolder created");
        }

        @SuppressLint("SetTextI18n")
        private void bindItem(@NonNull Item item) {
            titleTextView.setText(item.getTitle());
            nameTextView.setText("By: " + item.getFullName());
            descriptionTextView.setText(item.getDescription());
            Glide.with(itemView.getContext()).load(item.getImageUrl()).into(mediaImageView);
            Log.d(TAG, "Item bound: " + item.getTitle());
        }
    }
}