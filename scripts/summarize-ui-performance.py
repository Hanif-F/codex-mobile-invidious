#!/usr/bin/env python3
"""Summarize raw Macrobenchmark samples without pooling iterations or dropping outliers."""
import argparse
import json
import statistics
from pathlib import Path


def percentile(samples, p):
    ordered = sorted(samples)
    position = (len(ordered)-1)*p/100
    lower = int(position)
    upper = min(lower+1,len(ordered)-1)
    return ordered[lower]+(ordered[upper]-ordered[lower])*(position-lower)


def summarize(path):
    raw = json.loads(path.read_text())
    journeys = []
    for benchmark in raw['benchmarks']:
        metrics = {}
        for name, values in benchmark.get('sampledMetrics',{}).items():
            iterations = []
            for index, samples in enumerate(values['runs']):
                if not samples:
                    raise ValueError(f'{path}: {name} iteration {index} has no samples')
                run = dict(iteration=index,frameCount=len(samples),**{f'p{p}':percentile(samples,p) for p in (50,90,95,99)})
                if name == 'frameOverrunMs':
                    run['positiveOverrunFraction'] = sum(v>0 for v in samples)/len(samples)
                iterations.append(run)
            metrics[name] = dict(iterations=iterations,median={key:statistics.median(run[key] for run in iterations)
                for key in iterations[0] if key != 'iteration'})
        journeys.append(dict(name=benchmark['name'],className=benchmark.get('className'),metrics=metrics,
            frameCounts=benchmark.get('metrics',{}).get('frameCount',{}).get('runs',[])))
    return dict(raw=str(path.resolve()),context=raw.get('context'),journeys=journeys)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('inputs',nargs='+',type=Path)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    reports=[summarize(path) for path in args.inputs]
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(dict(method='median of per-iteration linear percentiles; all frames retained',runs=reports),indent=2)+'\n')
    for report in reports:
        for journey in report['journeys']:
            overrun=journey['metrics'].get('frameOverrunMs',{}).get('median',{})
            print(f"{journey['name']}: P95 overrun {overrun.get('p95')} ms; positive fraction {overrun.get('positiveOverrunFraction')}")

if __name__=='__main__':
    main()
