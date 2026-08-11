#include "core/render/scene_spaces.hpp"
#include <glm/gtc/matrix_transform.hpp>
#include <cassert>
#include <limits>
#include <stdexcept>

#undef assert
#define assert(condition)                                                                                              \
    do {                                                                                                               \
        if (!(condition)) throw std::runtime_error(#condition);                                                        \
    } while (false)

int main() {
    mcvr::scene::Spaces spaces;
    mcvr::scene::SpacePose parent, child;
    parent.matrix = glm::translate(glm::dmat4(1), glm::dvec3(30000000.125, 100, -30000000.375));
    child.matrix = glm::translate(glm::dmat4(1), glm::dvec3(.0625, 2, .125));
    spaces.put(1, 0, parent);
    spaces.put(2, 1, child);
    auto first = spaces.at(2);
    assert(!first.lighting.configured);
    assert(first.matrix[3].x == 30000000.1875);
    assert(first.matrix[3].z == -30000000.25);
    spaces.put(3, 0, child);
    auto independentVersion = spaces.version(3);
    auto childVersion = spaces.version(2);
    auto revision = spaces.revision();
    spaces.put(1, 0, parent);
    assert(spaces.revision() == revision);
    parent.matrix = glm::rotate(parent.matrix, .6, glm::dvec3(0, 1, 0));
    parent.normal = glm::mat3(parent.matrix);
    spaces.put(1, 0, parent);
    auto moved = spaces.at(2);
    assert(spaces.version(2) != childVersion);
    assert(spaces.version(3) == independentVersion);
    assert(moved.matrix == parent.matrix * child.matrix);
    assert(first.matrix != moved.matrix); // An already captured frame is immutable by value.
    auto light = mcvr::scene::SpacePose::Lighting{true, 7, .35f, glm::translate(glm::dmat4(1), glm::dvec3(8, 64, -3))};
    spaces.lighting(1, light);
    auto inherited = spaces.at(2).lighting;
    assert(inherited.configured && inherited.scene == 7 && inherited.sky == .35f);
    assert(inherited.matrix == light.matrix * child.matrix);
    light.scene = -1;
    light.sky = .1f;
    spaces.lighting(2, light);
    assert(spaces.at(2).lighting.scene == -1 && spaces.at(2).lighting.matrix == light.matrix);
    light.scene = 0;
    spaces.lighting(2, light);
    assert(spaces.at(2).lighting.matrix == spaces.at(2).matrix);
    auto saved = spaces.at(2);
    mcvr::scene::SpacePose::SectionColour gradient{{0, -1.f / 30, 0, 100.f / 30}, {.172f, .156f, .218f, 1}};
    spaces.sectionColour(1, gradient);
    assert(spaces.at(2).sectionColour == gradient);
    auto colourVersion = spaces.version(2);
    spaces.sectionColour(1, gradient);
    assert(spaces.version(2) == colourVersion);
    auto localPlane = glm::transpose(spaces.at(2).matrix) * glm::dvec4(gradient.plane);
    auto localPoint = glm::dvec4(0, -10, 0, 1);
    assert(std::abs(glm::dot(localPlane, localPoint) -
                    glm::dot(glm::dvec4(gradient.plane), spaces.at(2).matrix * localPoint)) < 1e-8);
    assert(std::abs(glm::dot(glm::dvec4(gradient.plane), glm::dvec4(0, 85, 0, 1)) - .5) < 1e-6);
    spaces.sectionColour(1, {});
    assert(spaces.at(2).sectionColour.target.w == 0);
    parent.matrix[3].x += .125;
    spaces.put(1, 0, parent);
    assert(saved.matrix != spaces.at(2).matrix && saved.lighting.matrix == saved.matrix);
    moved = spaces.at(2);
    bool rejected = false;
    try {
        spaces.put(1, 2, parent);
    } catch (const std::invalid_argument &) { rejected = true; }
    assert(rejected && spaces.at(2).matrix == moved.matrix);
    rejected = false;
    try {
        spaces.remove(1);
    } catch (const std::logic_error &) { rejected = true; }
    assert(rejected);
    child.matrix[0][0] = std::numeric_limits<double>::quiet_NaN();
    rejected = false;
    try {
        spaces.put(2, 1, child);
    } catch (const std::invalid_argument &) { rejected = true; }
    assert(rejected && spaces.at(2).matrix == moved.matrix);
    spaces.remove(2);
    assert(!spaces.contains(2) && spaces.at(2).matrix == moved.matrix);
    spaces.remove(1);
    spaces.remove(3);
    assert(spaces.size() == 0 && spaces.at(0).matrix == glm::dmat4(1));
    spaces.beginFrame();
    rejected = false;
    try {
        spaces.at(2);
    } catch (const std::out_of_range &) { rejected = true; }
    assert(rejected);
    mcvr::scene::PoseMotion motion;
    motion.before.position = {30000000.125, 2, -30000000.375};
    motion.after.position = {30000002.125, 6, -30000004.375};
    motion.after.rotation = glm::angleAxis(.8, glm::dvec3(0, 1, 0));
    motion.before.scale = {-2, 3, 4};
    motion.after.scale = {-4, 5, 6};
    motion.start = 10;
    motion.end = 12;
    spaces.motion(4, 0, motion, 10);
    auto original = spaces.at(4);
    spaces.sample(4, 11);
    auto halfway = spaces.at(4);
    assert(halfway.matrix[3].x == 30000001.125 && halfway.matrix[3].z == -30000002.375);
    assert(halfway.matrix == motion.at(11));
    assert(original.matrix == motion.at(10));
    spaces.sample(4, 20);
    assert(spaces.at(4).matrix == motion.at(12));
    spaces.put(4, 0, child = {});
    assert(spaces.at(4).matrix == glm::dmat4(1));
    rejected = false;
    try {
        spaces.sample(4, 11);
    } catch (const std::invalid_argument &) { rejected = true; }
    assert(rejected);
    motion.after.scale.x = 2;
    rejected = false;
    try {
        spaces.motion(4, 0, motion, 11);
    } catch (const std::invalid_argument &) { rejected = true; }
    assert(rejected && spaces.at(4).matrix == glm::dmat4(1));
    spaces.clear();
}
