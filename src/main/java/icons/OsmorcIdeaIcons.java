/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package icons;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.IconUtil;

import javax.swing.Icon;
import javax.swing.SwingConstants;

/**
 * NOTE THIS FILE IS AUTO-GENERATED
 * DO NOT EDIT IT BY HAND, run build/scripts/icons.gant instead
 */
public class OsmorcIdeaIcons {
    private static Icon load(String path) {
        return IconLoader.getIcon(path, OsmorcIdeaIcons.class);
    }

    public static final Icon Bnd = load("/icons/bnd.png"); // 16x16
    public static final Icon BndLaunch = load("/icons/bndLaunch.png"); // 16x16
    public static final Icon BndTest = load("/icons/bndTest.png"); // 16x16
    public static final Icon Osgi = load("/icons/osgi.png"); // 16x16
    public static final Icon ExportedPackage = createLayeredIcon(AllIcons.Nodes.Package, AllIcons.General.Add);
    public static final Icon PrivatePackage = createLayeredIcon(AllIcons.Nodes.Package, AllIcons.General.Remove);
    public static final Icon NotIncludedPackage = createLayeredIcon(AllIcons.Nodes.Package, AllIcons.General.Error);


    private static Icon createLayeredIcon(Icon base, Icon overlay) {
        LayeredIcon layeredIcon = new LayeredIcon(2);
        layeredIcon.setIcon(base, 0);
        layeredIcon.setIcon(IconUtil.scale(overlay, null, 0.6f), 1, SwingConstants.SOUTH_EAST);
        return layeredIcon;
    }
}
