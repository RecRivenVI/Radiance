#pragma once
#include <memory>
#include <utility>

namespace mcvr::render {
// Texture parameters can be set before image storage exists. Storage initialization must retain
// this owner rather than resetting the source's already configured filtering/wrapping.
template <class Sampler, class Create>
void initializeSampler(std::shared_ptr<Sampler> &slot, Create &&create) {
    if (!slot) slot = create();
}

template <class Sampler, class Filter, class Mipmap>
bool matchesFilter(Sampler &sampler, Filter filter, Mipmap mipmap) {
    return sampler.vkSamplingMode() == filter && sampler.vkMipmapMode() == mipmap;
}
// Call under the texture/recreation locks. Initialization and reload publication are separate:
// setting an unchanged sampler is not a request to republish the texture generation.
template <class Sampler, class Matches, class Create, class Retire, class Publish>
bool updateSampler(
    std::shared_ptr<Sampler> &slot, Matches &&matches, Create &&create, Retire &&retire, Publish &&publish) {
    if (matches(*slot)) return false;
    auto replacement = create(); // Failure leaves the old owner and bindings intact.
    retire(slot);                // Retain old references before replacing their owner.
    slot = std::move(replacement);
    publish();
    return true;
}
} // namespace mcvr::render
