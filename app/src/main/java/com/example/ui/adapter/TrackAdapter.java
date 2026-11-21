package com.example.ui.adapter;

import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.example.R;
import com.example.data.AudioLibraryRepository;
import com.example.model.Track;
import com.example.ui.theme.NeonPalette;
import com.example.ui.theme.UiStyleMode;
import com.example.util.TimeFormatter;
import com.google.android.material.imageview.ShapeableImageView;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class TrackAdapter extends RecyclerView.Adapter<TrackAdapter.TrackViewHolder> {
    private static final String PAYLOAD_PLAY_STATE = "payload_play_state";
    private static final String PAYLOAD_STYLE = "payload_style";

    public interface OnTrackClickListener {
        void onTrackClicked(Track track, int position);
        void onTrackMoreClicked(Track track, View anchorView);
        void onTrackDeleteClicked(Track track, int position);
    }

    private final List<Track> items = new ArrayList<>();
    private final AudioLibraryRepository repository;
    private final OnTrackClickListener listener;
    private String currentTrackId = null;
    private boolean isPlaying = false;
    private NeonPalette activePalette = NeonPalette.DEFAULT;
    private UiStyleMode uiStyleMode = UiStyleMode.NEON_COSMIC;

    public TrackAdapter(AudioLibraryRepository repository, OnTrackClickListener listener) {
        this.repository = repository;
        this.listener = listener;
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        String id = items.get(position).getId();
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < id.length(); i++) {
            hash ^= id.charAt(i);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    public void submitList(List<Track> newTracks) {
        items.clear();
        if (newTracks != null) {
            items.addAll(newTracks);
        }
        notifyDataSetChanged();
    }

    public void setPlaybackState(String trackId, boolean playing) {
        String oldTrackId = this.currentTrackId;
        boolean oldPlaying = this.isPlaying;
        if (Objects.equals(oldTrackId, trackId) && oldPlaying == playing) {
            return;
        }
        this.currentTrackId = trackId;
        this.isPlaying = playing;

        int oldIdx = findIndexById(oldTrackId);
        int newIdx = findIndexById(trackId);
        if (oldIdx >= 0) {
            notifyItemChanged(oldIdx, PAYLOAD_PLAY_STATE);
        }
        if (newIdx >= 0 && newIdx != oldIdx) {
            notifyItemChanged(newIdx, PAYLOAD_PLAY_STATE);
        }
    }

    public void setThemePalette(NeonPalette palette) {
        NeonPalette next = palette != null ? palette : NeonPalette.DEFAULT;
        if (this.activePalette.primaryNeon == next.primaryNeon
                && this.activePalette.secondaryNeon == next.secondaryNeon
                && this.activePalette.cardGlassStart == next.cardGlassStart) {
            return;
        }
        this.activePalette = next;
        if (!items.isEmpty()) {
            notifyItemRangeChanged(0, items.size(), PAYLOAD_STYLE);
        }
    }

    public void setUiStyleMode(UiStyleMode mode) {
        UiStyleMode next = mode != null ? mode : UiStyleMode.NEON_COSMIC;
        if (this.uiStyleMode == next) return;
        this.uiStyleMode = next;
        if (!items.isEmpty()) {
            notifyItemRangeChanged(0, items.size(), PAYLOAD_STYLE);
        }
    }

    private int findIndexById(String trackId) {
        if (trackId == null) return -1;
        for (int i = 0; i < items.size(); i++) {
            if (trackId.equals(items.get(i).getId())) {
                return i;
            }
        }
        return -1;
    }

    @NonNull
    @Override
    public TrackViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_track, parent, false);
        TrackViewHolder holder = new TrackViewHolder(view);
        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (listener != null && pos >= 0 && pos < items.size()) {
                listener.onTrackClicked(items.get(pos), pos);
            }
        });
        holder.moreButton.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (listener != null && pos >= 0 && pos < items.size()) {
                listener.onTrackMoreClicked(items.get(pos), holder.moreButton);
            }
        });
        holder.deleteButton.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (listener != null && pos >= 0 && pos < items.size()) {
                listener.onTrackDeleteClicked(items.get(pos), pos);
            }
        });
        return holder;
    }

    @Override
    public void onBindViewHolder(
            @NonNull TrackViewHolder holder,
            int position,
            @NonNull List<Object> payloads) {
        if (!payloads.isEmpty()) {
            Track track = items.get(position);
            bindStylingOnly(holder, track);
            return;
        }
        onBindViewHolder(holder, position);
    }

    @Override
    public void onBindViewHolder(@NonNull TrackViewHolder holder, int position) {
        Track track = items.get(position);
        holder.titleText.setText(track.getTitle());
        holder.subtitleText.setText(track.getArtist() + "  •  " + TimeFormatter.formatDuration(track.getDurationMs()));
        holder.formatBadge.setText(track.getFormat());

        bindStylingOnly(holder, track);

        if (!track.getId().equals(holder.boundTrackId)) {
            holder.boundTrackId = track.getId();
            holder.coverArt.setImageResource(R.drawable.img_default_album_art);
            int artPx = Math.round(52 * holder.density);
            repository.loadAlbumArtAsync(track, artPx, (loadedTrackId, bitmap) -> {
                if (loadedTrackId.equals(holder.boundTrackId) && bitmap != null && !bitmap.isRecycled()) {
                    holder.coverArt.setImageBitmap(bitmap);
                }
            });
        }
    }

    private void bindStylingOnly(@NonNull TrackViewHolder holder, @NonNull Track track) {
        boolean isCurrent = track.getId().equals(currentTrackId);
        float density = holder.density;

        if (uiStyleMode == UiStyleMode.RETRO_VINYL) {
            holder.subtitleText.setTextColor(0xFF7A5638);
            holder.formatBadge.setTextColor(0xFF8B4E24);
            holder.moreButton.setColorFilter(0xFF6B492D);
            holder.deleteButton.setColorFilter(0xFFB45309);
            holder.titleText.setTextColor(0xFF2D1B10);
            if (isCurrent) {
                holder.ensureHighlightBg(density);
                holder.highlightBg.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
                holder.highlightBg.setColors(new int[]{0xFFFDE5B4, 0xFFFDE5B4});
                holder.highlightBg.setStroke(Math.max(1, Math.round(1.2f * density)), 0xFFD99B4E);
                holder.rowContainer.setBackground(holder.highlightBg);
                holder.playIndicator.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play_circle_outline);
                holder.playIndicator.setColorFilter(0xFFC25216);
            } else {
                holder.rowContainer.setBackground(null);
                holder.playIndicator.setImageResource(R.drawable.ic_play_circle_outline);
                holder.playIndicator.setColorFilter(0xFF8C6B4B);
            }
        } else if (uiStyleMode == UiStyleMode.LIQUID_GLASS) {
            holder.subtitleText.setTextColor(0xFF4F46E5);
            holder.formatBadge.setTextColor(0xFF6D28D9);
            holder.moreButton.setColorFilter(0xFF4338CA);
            holder.deleteButton.setColorFilter(0xFFE11D48);
            holder.titleText.setTextColor(0xFF1E1B4B);
            if (isCurrent) {
                holder.ensureHighlightBg(density);
                holder.highlightBg.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
                holder.highlightBg.setColors(new int[]{0xCCB8A9FF, 0xCCA5F3FC});
                holder.highlightBg.setStroke(Math.max(1, Math.round(1.2f * density)), 0xEEFFFFFF);
                holder.rowContainer.setBackground(holder.highlightBg);
                holder.playIndicator.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play_circle_outline);
                holder.playIndicator.setColorFilter(0xFF6D28D9);
            } else {
                holder.rowContainer.setBackground(null);
                holder.playIndicator.setImageResource(R.drawable.ic_play_circle_outline);
                holder.playIndicator.setColorFilter(0xFF6366F1);
            }
        } else {
            holder.subtitleText.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.text_secondary));
            holder.formatBadge.setTextColor(activePalette.secondaryNeon);
            holder.moreButton.setColorFilter(ContextCompat.getColor(holder.itemView.getContext(), R.color.text_secondary));
            holder.deleteButton.setColorFilter(0xFFF472B6);
            if (isCurrent) {
                holder.ensureHighlightBg(density);
                int fill = NeonPalette.withAlpha(activePalette.cardGlassStart, 0xCC);
                holder.highlightBg.setColors(new int[]{fill, fill});
                holder.highlightBg.setStroke(Math.max(1, Math.round(1.5f * density)), activePalette.primaryNeon);
                holder.rowContainer.setBackground(holder.highlightBg);
                holder.titleText.setTextColor(activePalette.secondaryNeon);
                holder.playIndicator.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play_circle_outline);
                holder.playIndicator.setColorFilter(activePalette.secondaryNeon);
            } else {
                holder.rowContainer.setBackground(null);
                holder.titleText.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.text_primary));
                holder.playIndicator.setImageResource(R.drawable.ic_play_circle_outline);
                holder.playIndicator.clearColorFilter();
            }
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class TrackViewHolder extends RecyclerView.ViewHolder {
        final View rowContainer;
        final ShapeableImageView coverArt;
        final TextView titleText;
        final TextView subtitleText;
        final TextView formatBadge;
        final ImageView playIndicator;
        final ImageButton deleteButton;
        final ImageButton moreButton;
        final float density;
        GradientDrawable highlightBg;
        String boundTrackId;

        TrackViewHolder(@NonNull View itemView) {
            super(itemView);
            density = itemView.getResources().getDisplayMetrics().density;
            rowContainer = itemView.findViewById(R.id.track_row_container);
            coverArt = itemView.findViewById(R.id.item_cover_art);
            titleText = itemView.findViewById(R.id.item_title);
            subtitleText = itemView.findViewById(R.id.item_subtitle);
            formatBadge = itemView.findViewById(R.id.item_format_badge);
            playIndicator = itemView.findViewById(R.id.item_play_indicator);
            deleteButton = itemView.findViewById(R.id.item_delete_button);
            moreButton = itemView.findViewById(R.id.item_more_button);
        }

        void ensureHighlightBg(float density) {
            if (highlightBg == null) {
                highlightBg = new GradientDrawable();
                highlightBg.setCornerRadius(14f * density);
            }
        }
    }
}
