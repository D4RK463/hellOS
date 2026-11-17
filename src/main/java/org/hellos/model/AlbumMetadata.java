// SPDX-License-Identifier: GPL-3.0-or-later
package org.hellos.model;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Album art returned by {@code browse/retrieve_metadata}. */
public record AlbumMetadata(String albumId, List<Image> images) {

    public AlbumMetadata {
        Objects.requireNonNull(albumId, "albumId");
        images = List.copyOf(images);
    }

    /** The image with the largest width. */
    public Optional<Image> largestImage() {
        return images.stream().max(Comparator.comparingInt(Image::width));
    }

    /** An image in one resolution. */
    public record Image(String url, int width) {
        public Image {
            Objects.requireNonNull(url, "url");
        }
    }
}
