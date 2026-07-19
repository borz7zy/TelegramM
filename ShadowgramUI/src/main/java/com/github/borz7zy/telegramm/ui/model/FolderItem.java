package com.github.borz7zy.telegramm.ui.model;

public final class FolderItem {

    public static final int ID_ALL = -1;

    public final int id;
    public final String title;
    public final boolean isSelected;

    public FolderItem(int id, String title, boolean isSelected) {
        this.id = id;
        this.title = title;
        this.isSelected = isSelected;
    }

    public FolderItem withSelected(boolean selected) {
        return new FolderItem(id, title, selected);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FolderItem)) return false;
        FolderItem that = (FolderItem) o;
        return id == that.id
                && isSelected == that.isSelected
                && title.equals(that.title);
    }

    @Override
    public int hashCode() {
        return 31 * id + title.hashCode();
    }
}