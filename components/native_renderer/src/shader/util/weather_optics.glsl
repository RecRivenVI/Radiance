#ifndef WEATHER_OPTICS_GLSL
#define WEATHER_OPTICS_GLSL
float weatherDirectStrength(float rain, float thunder) {
    return mix(1.0, 0.35, clamp(rain, 0.0, 1.0)) * (1.0 - clamp(thunder, 0.0, 1.0));
}
float weatherAtmosphereStrength(float rain, float thunder) {
    return mix(1.0, 0.5, clamp(rain, 0.0, 1.0)) * mix(1.0, 0.3, clamp(thunder, 0.0, 1.0));
}
// Extra wet aerosols use the same extinction in sky integration and light visibility.
// The dry-atmosphere LUT remains valid as weather changes.
float weatherDensity(float height,float rain) { return rain*4e-4*exp(-max(height,0.0)/1200.0); }
vec3 weatherTransmittance(float height,vec3 direction,float rain) {
    float distance=12000.0/max(abs(direction.y),.1), opticalDepth=0;
    for(int i=0;i<8;i++)opticalDepth+=weatherDensity(height+direction.y*(float(i)+.5)*distance/8.0,rain)*distance/8.0;
    return exp(-vec3(opticalDepth));
}
#endif
