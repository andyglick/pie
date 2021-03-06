package mb.pie.vfs.list;

import mb.pie.vfs.path.PPath;

public class AllPathMatcher implements PathMatcher {
    private static final long serialVersionUID = 1L;


    @Override public boolean matches(PPath path, PPath root) {
        return true;
    }

    @Override public int hashCode() {
        return 0;
    }


    @Override public boolean equals(Object obj) {
        if(this == obj)
            return true;
        if(obj == null)
            return false;
        if(getClass() != obj.getClass())
            return false;
        return true;
    }

    @Override public String toString() {
        return "AllPathMatcher";
    }
}
