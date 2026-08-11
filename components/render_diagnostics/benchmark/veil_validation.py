"""One packaged correctness session for supported Veil resources, with Voxy excluded."""
import argparse
from platform_integration import run
from takeover_experiment import WORKSPACE_SOFT_LIMIT_GIB, WORKSPACE_HARD_LIMIT_GIB

if __name__ == '__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('core')
    parser.add_argument('extra')
    parser.add_argument('--workspace-limit-gib',type=int,choices=range(1,WORKSPACE_HARD_LIMIT_GIB+1),default=WORKSPACE_SOFT_LIMIT_GIB)
    args=parser.parse_args()
    run('sources',core_override=args.core,extra_override=args.extra,
        input_names=('create','sable','aeronautics_bundled'),
        expected_sources='radiance_extra:sable,radiance_extra:flywheel,radiance_extra:simulated,radiance_extra:veil,radiance_extra:outlines',
        veil_resources=True,workspace_limit_gib=args.workspace_limit_gib,instance_name="veil")
