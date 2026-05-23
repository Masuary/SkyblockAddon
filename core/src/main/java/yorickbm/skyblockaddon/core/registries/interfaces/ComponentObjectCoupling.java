package yorickbm.skyblockaddon.core.registries.interfaces;

import yorickbm.skyblockaddon.core.util.DataComponent;

import java.util.Optional;

public interface ComponentObjectCoupling<T> {
    Optional<T> getDataForComponent(DataComponent component);
}
