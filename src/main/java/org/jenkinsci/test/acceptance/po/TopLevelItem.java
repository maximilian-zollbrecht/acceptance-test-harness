package org.jenkinsci.test.acceptance.po;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import com.google.common.base.Objects;
import com.google.inject.Injector;

/**
 * Super class for top level items. 
 * Top level items include {@link Job}s and other non-buildable items
 * such as {@link FolderItem}s.
 * Use {@link Describable} annotation to register an implementation.
 */
public abstract class TopLevelItem extends ContainerPageObject {
    public String name;

    public List<Parameter> getParameters() {
        return parameters;
    }

    private List<Parameter> parameters = new ArrayList<>();

    public TopLevelItem(Injector injector, URL url, String name) {
        super(injector, url);
        this.name = name;
    }

    public void setName(String name) {
        ensureConfigPage();
        this.name = name;
        control("/name").set(name);
    }

    /**
     * "Casts" this object into a subtype by creating the specified type.
     */
    public <T extends TopLevelItem> T as(Class<T> type) {
        if (type.isInstance(this)) {
            return type.cast(this);
        }
        return newInstance(type, injector, url, name);
    }

    @Override
    public String toString() {
        return String.format("%s[%s]", getClass().getSimpleName(), url);
    }

    @Override
    public boolean equals(Object other) {
        if (other == null) return false;
        if (this == other) return true;

        if (!(other instanceof TopLevelItem)) return false;
        
        // Equals and hashCode must be consistent with each other.
        // Keep identity based on class and url, as in hashCode before.
        // Shouldn't be url alone?
        TopLevelItem rhs = (TopLevelItem) other;
        return getClass().equals(rhs.getClass()) && Objects.equal(url, rhs.url);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode() ^ url.hashCode();
    }
}
