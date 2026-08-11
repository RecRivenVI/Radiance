#pragma once

#include "common/shared.hpp"
#include "common/render_abi.hpp"
#include "common/singleton.hpp"
#include "core/all_extern.hpp"
#include "core/vulkan/all_core_vulkan.hpp"

#include <condition_variable>
#include <future>
#include <thread>

class Framework;
class Chunks;
class Entities;
class Instancing;
namespace mcvr {
class VoxySections;
}

class World : public SharedObject<World> {
  public:
    enum VertexFormats {
        POSITION_COLOR_TEXTURE_LIGHT_NORMAL = mcvr::abi::FORMAT_POSITION_COLOR_TEXTURE_LIGHT_NORMAL,
        POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL = mcvr::abi::FORMAT_POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,
        POSITION_TEXTURE_COLOR_LIGHT = mcvr::abi::FORMAT_POSITION_TEXTURE_COLOR_LIGHT,
        POSITION = mcvr::abi::FORMAT_POSITION,
        POSITION_COLOR = mcvr::abi::FORMAT_POSITION_COLOR,
        LINES = mcvr::abi::FORMAT_LINES,
        POSITION_COLOR_LIGHT = mcvr::abi::FORMAT_POSITION_COLOR_LIGHT,
        POSITION_TEXTURE = mcvr::abi::FORMAT_POSITION_TEXTURE,
        POSITION_TEXTURE_COLOR = mcvr::abi::FORMAT_POSITION_TEXTURE_COLOR,
        POSITION_COLOR_TEXTURE_LIGHT = mcvr::abi::FORMAT_POSITION_COLOR_TEXTURE_LIGHT,
        POSITION_TEXTURE_LIGHT_COLOR = mcvr::abi::FORMAT_POSITION_TEXTURE_LIGHT_COLOR,
        POSITION_TEXTURE_COLOR_NORMAL = mcvr::abi::FORMAT_POSITION_TEXTURE_COLOR_NORMAL,
        PBR_TRIANGLE = mcvr::abi::FORMAT_PBR_TRIANGLE,
        PBR_COMPACT_V1 = mcvr::abi::FORMAT_PBR_COMPACT_V1,
        CAPTURED_LINES = mcvr::abi::FORMAT_CAPTURED_LINES,
        NUM_VERTEX_FORMATS,
    };

    enum GeometryTypes {
        SHADOW = mcvr::abi::GEOMETRY_SHADOW,
        WORLD_SOLID = mcvr::abi::GEOMETRY_WORLD_SOLID,
        WORLD_TRANSPARENT = mcvr::abi::GEOMETRY_WORLD_TRANSPARENT,
        WORLD_NO_REFLECT = mcvr::abi::GEOMETRY_WORLD_NO_REFLECT,
        WORLD_CLOUD = mcvr::abi::GEOMETRY_WORLD_CLOUD,
        BOAT_WATER_MASK = mcvr::abi::GEOMETRY_BOAT_WATER_MASK,
        END_PORTAL = mcvr::abi::GEOMETRY_END_PORTAL,
        END_GATE_WAY = mcvr::abi::GEOMETRY_END_GATEWAY,
        NUM_GEOMETRY_TYPES,
    };

    enum Coordinates {
        WORLD = mcvr::abi::COORDINATE_WORLD,
        CAMERA = mcvr::abi::COORDINATE_CAMERA,
        CAMERA_SHIFT = mcvr::abi::COORDINATE_CAMERA_SHIFT,
    };

    enum class DrawMode {
        LINES = mcvr::abi::MODE_LINES,
        LINE_STRIP = mcvr::abi::MODE_LINE_STRIP,
        DEBUG_LINES = mcvr::abi::MODE_DEBUG_LINES,
        DEBUG_LINE_STRIP = mcvr::abi::MODE_DEBUG_LINE_STRIP,
        TRIANGLES = mcvr::abi::MODE_TRIANGLES,
        TRIANGLE_STRIP = mcvr::abi::MODE_TRIANGLE_STRIP,
        TRIANGLE_FAN = mcvr::abi::MODE_TRIANGLE_FAN,
        QUADS = mcvr::abi::MODE_QUADS,
    };

    World(std::shared_ptr<Framework> framework);

    void resetFrame();
    bool &shouldRender();

    std::shared_ptr<Chunks> chunks();
    std::shared_ptr<Entities> entities();
    std::shared_ptr<Instancing> instancing();
    std::shared_ptr<mcvr::VoxySections> voxy();

    void setCameraPos(glm::dvec3 cameraPos);
    glm::dvec3 getCameraPos();

    void close();
    // The client left its world. Waits for the GPU, then frees the scene (chunks, entity caches and the
    // world pipeline's last-frame TLAS and history) instead of keeping it until the next world or shutdown.
    void releaseScene();

  private:
    std::shared_ptr<Chunks> chunks_;
    std::shared_ptr<Entities> entities_;
    std::shared_ptr<Instancing> instancing_;
    std::shared_ptr<mcvr::VoxySections> voxy_;

    glm::dvec3 cameraPos_ = {0, 0, 0};

    bool shouldRenderWorld_ = false;
};
